"""Read-only gh export of a real run/attempt, including a slide-sized stage table."""
import argparse
import json
import re
import shutil
import subprocess
from pathlib import Path

from release_evidence import command, read_json, same_run, summarize, write_json

JOB_IDS = {"Backend build and unit tests": "backend-test", "Frontend build and lint": "frontend-build",
           "Secret scanning (advisory)": "secret-scan", "Dependency vulnerability scan (advisory)": "dependency-scan",
           "Docker image build": "docker-build", "Deploy to staging": "deploy-staging", "Deploy to production": "deploy-production"}


def step_result(job, name):
    return next((step.get("conclusion") or step.get("status", "unknown")
                 for step in job.get("steps", []) if step["name"] == name), "not_recorded")


def observed_summary(run, jobs, attempt):
    by_name = {job["name"]: job for job in jobs}
    stages = [
        ("Backend tests", "Backend build and unit tests", "Run unit and Testcontainers integration tests"),
        ("Frontend build/lint", "Frontend build and lint", "Build"),
        ("Repository scan (advisory)", "Dependency vulnerability scan (advisory)", "Run Trivy against the repository"),
        ("Backend image scan (advisory)", "Docker image build", "Scan backend image for vulnerabilities"),
        ("Frontend image scan (advisory)", "Docker image build", "Scan frontend image for vulnerabilities"),
        ("GHCR publish", "Docker image build", "Push images"),
        ("Staging restart", "Deploy to staging", "Pull and restart"),
        ("Staging health smoke", "Deploy to staging", "Smoke test"),
        ("Production health smoke", "Deploy to production", "Smoke test"),
    ]
    rows = [(label, step_result(by_name.get(name, {}), step), by_name.get(name, {}).get("html_url", run["html_url"]))
            for label, name, step in stages]
    chain = all(result == "success" for _, result, _ in rows[:-1])
    lines = ["# Observed pipeline — real GitHub run", "", f"Source: {run['html_url']}",
             f"Commit: `{run['head_sha']}` · attempt {attempt} · event {run['event']} · branch `{run['head_branch']}`",
             f"Run conclusion: {run.get('conclusion') or run['status']}", "",
             "| Stage | GitHub-recorded step result | Source |", "|---|---|---|"]
    lines += [f"| {label} | {result} | [job]({url}) |" for label, result, url in rows]
    lines += ["", f"Observed test → scan → publish → staging health chain: **{'YES' if chain else 'NO'}**", "",
              "Step results are not vulnerability counts. Advisory success does not prove a clean scan.",
              "This table does not invent missing scan reports/digests or assert the current live version.",
              "See release-evidence.json for retained reports, digests and evidence-completeness checks."]
    return "\n".join(lines) + "\n"


def export(repo, run_id, attempt, directory):
    if directory.exists() and any(directory.iterdir()):
        raise ValueError("Use an empty output directory to avoid mixing runs/attempts")
    directory.mkdir(parents=True, exist_ok=True)
    run = json.loads(command("gh", "api", f"repos/{repo}/actions/runs/{run_id}"))
    attempt = str(attempt or run["run_attempt"])
    if int(attempt) > int(run["run_attempt"]):
        raise ValueError("Requested attempt does not exist")
    # Use attempt-specific run/step metadata, not the latest rerun's conclusion.
    run = json.loads(command("gh", "api", f"repos/{repo}/actions/runs/{run_id}/attempts/{attempt}"))
    pages = json.loads(command("gh", "api", "--paginate", "--slurp",
                              f"repos/{repo}/actions/runs/{run_id}/attempts/{attempt}/jobs?per_page=100"))
    jobs = [job for page in pages for job in page["jobs"]]
    # Retain selected public metadata only; no logs, environments or secrets.
    metadata = {key: run.get(key) for key in ("id", "run_attempt", "head_sha", "head_branch", "event", "status",
                                             "conclusion", "html_url", "created_at", "updated_at")}
    write_json(directory / "github-run.json", metadata)
    write_json(directory / "github-jobs.json", jobs)
    context = {"repository": repo, "commit": run["head_sha"], "run_id": str(run_id), "run_attempt": attempt,
               "ref": "refs/heads/" + run["head_branch"] if run["event"] != "pull_request" else "pull_request",
               "event": run["event"], "run_url": run["html_url"], "recorded_at": run["updated_at"]}
    retained_dir = directory / "retained"
    download = subprocess.run(["gh", "run", "download", str(run_id), "--repo", repo,
                               "--name", f"release-evidence-{attempt}", "--dir", str(retained_dir)],
                              text=True, capture_output=True, timeout=120)
    retained = read_json(retained_dir / "release-evidence.json")
    if download.returncode != 0 or not same_run(retained, context):
        needs = {JOB_IDS[job["name"]]: {"result": job.get("conclusion") or job.get("status", "unknown"), "outputs": {}}
                 for job in jobs if job["name"] in JOB_IDS}
        # Historical runs did not retain structured reports/digests. Keep unknown,
        # even if the GitHub job is green; do not reverse-engineer a zero count.
        summarize(directory, context, needs)
    else:
        write_json(directory / "release-evidence.json", retained)
        for name in ("release-evidence.md", "slide-excerpt.md"):
            if (retained_dir / name).is_file():
                shutil.copyfile(retained_dir / name, directory / name)
    (directory / "observed-pipeline.md").write_text(observed_summary(run, jobs, attempt), encoding="utf-8")
    return directory


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", default="NUS-SE-34FT-GROUP4/c2cTradePlatform")
    parser.add_argument("--run", type=int, required=True)
    parser.add_argument("--attempt", type=int)
    parser.add_argument("--directory", type=Path, required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", args.repo) or args.run < 1 or (args.attempt is not None and args.attempt < 1):
        parser.error("Invalid repository, run ID or attempt")
    try:
        export(args.repo, args.run, args.attempt, args.directory)
    except (ValueError, OSError, subprocess.SubprocessError) as error:
        # Raw subprocess stderr may contain local auth diagnostics; don't retain it.
        parser.exit(1, f"Export failed ({type(error).__name__}); check gh authentication, run ID and output directory.\n")


if __name__ == "__main__":
    main()
