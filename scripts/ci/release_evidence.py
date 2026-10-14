"""Standard-library-only release evidence; never infer success from missing data."""
import argparse
import json
import os
import re
import subprocess
from datetime import datetime, timezone
from pathlib import Path

SCANS = {"repository": "trivy-repository.json", "backend": "trivy-backend.json", "frontend": "trivy-frontend.json"}
DIGEST = re.compile(r"sha256:[0-9a-f]{64}\Z")


def context_from_env():
    repo = os.environ.get("GITHUB_REPOSITORY", "unknown")
    run = os.environ.get("GITHUB_RUN_ID", "unknown")
    return {
        "repository": repo, "commit": os.environ.get("GITHUB_SHA", "unknown"),
        "run_id": run, "run_attempt": os.environ.get("GITHUB_RUN_ATTEMPT", "1"),
        "ref": os.environ.get("GITHUB_REF", "unknown"), "event": os.environ.get("GITHUB_EVENT_NAME", "unknown"),
        "run_url": f"{os.environ.get('GITHUB_SERVER_URL', 'https://github.com')}/{repo}/actions/runs/{run}",
        "recorded_at": datetime.now(timezone.utc).isoformat(),
    }


def write_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def read_json(path):
    try:
        return json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None


def command(*args):
    # No shell expansion, raw command output or authentication details in artifacts.
    return subprocess.run(args, check=True, text=True, capture_output=True, timeout=60).stdout.strip()


def same_run(record, context):
    return (isinstance(record, dict) and isinstance(record.get("context"), dict)
            and all(str(record["context"].get(key)) == str(context[key])
                    for key in ("repository", "commit", "run_id", "run_attempt")))


def capture_images(directory, context, builds, publish):
    images = []
    digest_error = False
    for app, build in builds.items():
        reference = f"ghcr.io/{context['repository'].split('/')[0].lower()}/c2c-sectrade-{app}:{context['commit']}"
        image = {"app": app, "reference": reference, "build_outcome": build,
                 "local_image_id": None, "registry_digest": None, "immutable_reference": None,
                 "publication": "not_requested" if context["ref"] != "refs/heads/main" else "not_verified"}
        if build == "success":
            try:
                image["local_image_id"] = command("docker", "image", "inspect", "--format", "{{.Id}}",
                                                    f"c2c-sectrade-{app}:{context['commit']}")
            except (subprocess.SubprocessError, OSError):
                image["local_image_id"] = None
        if context["ref"] == "refs/heads/main" and publish == "success":
            try:
                manifest = json.loads(command("docker", "buildx", "imagetools", "inspect", reference,
                                              "--format", "{{json .Manifest}}"))
                digest = manifest.get("digest", "")
                if not DIGEST.fullmatch(digest):
                    raise ValueError("Invalid registry digest")
                image.update(registry_digest=digest, immutable_reference=reference.split(":")[0] + "@" + digest,
                             publication="verified_registry")
            except (subprocess.SubprocessError, OSError, ValueError, AttributeError, TypeError):
                image["publication"] = "digest_capture_failed"
                digest_error = True
        images.append(image)
    write_json(Path(directory) / "images.json", {"context": context, "publish_step": publish, "images": images})
    return not digest_error


def capture_deployment(directory, context, configured, restart, smoke, record, rollback):
    result = {
        "context": context, "target": "staging", "configured": configured == "true",
        "restart": restart or "skipped", "smoke": smoke or "skipped", "record_live_version": record or "skipped",
        "rollback": rollback or "skipped",
        "verified": configured == "true" and restart == smoke == record == "success" and rollback in ("", "skipped"),
    }
    write_json(Path(directory) / "staging-deployment.json", result)
    return result


def scan_result(path, outcome):
    report = read_json(path)
    result = {"step_outcome": outcome or "unknown", "status": "not_retained", "high": None, "critical": None}
    if (not isinstance(report, dict) or report.get("SchemaVersion") != 2
            or not all(isinstance(report.get(key), str) and report[key] for key in ("ArtifactName", "ArtifactType"))):
        return result
    entries = report.get("Results", [])
    if not isinstance(entries, list):
        return result
    high = critical = 0
    for entry in entries:
        if not isinstance(entry, dict) or not isinstance(entry.get("Vulnerabilities") or [], list):
            return result
        for finding in entry.get("Vulnerabilities") or []:
            if not isinstance(finding, dict):
                return result
            high += finding.get("Severity") == "HIGH"
            critical += finding.get("Severity") == "CRITICAL"
    result.update(high=high, critical=critical)
    if outcome == "success":
        result["status"] = "findings" if high + critical else "clean_in_scope"
    elif outcome == "failure":
        result["status"] = "findings" if high + critical else "scanner_error"
    else:
        result["status"] = "outcome_unknown"
    return result


def stage(needs, job):
    return needs.get(job, {}).get("result", "unknown")


def summarize(directory, context, needs):
    directory = Path(directory)
    release = read_json(directory / "images.json")
    deployment = read_json(directory / "staging-deployment.json")
    release = release if same_run(release, context) else None
    deployment = deployment if same_run(deployment, context) else None
    outcomes = {
        "repository": needs.get("dependency-scan", {}).get("outputs", {}).get("scan_outcome"),
        "backend": needs.get("docker-build", {}).get("outputs", {}).get("backend_scan_outcome"),
        "frontend": needs.get("docker-build", {}).get("outputs", {}).get("frontend_scan_outcome"),
    }
    scans = {name: scan_result(directory / filename, outcomes[name]) for name, filename in SCANS.items()}
    images = release.get("images", []) if release else []
    if not isinstance(images, list):
        images = []
    images = [image for image in images if isinstance(image, dict)
              and all(isinstance(image.get(key), str) for key in ("app", "reference", "publication"))]
    published = (release is not None and release.get("publish_step") == "success" and len(images) == 2
                 and {image.get("app") for image in images} == {"backend", "frontend"}
                 and all(image.get("publication") == "verified_registry"
                         and isinstance(image.get("registry_digest"), str)
                         and DIGEST.fullmatch(image["registry_digest"])
                         and image["reference"] == f"ghcr.io/{context['repository'].split('/')[0].lower()}/c2c-sectrade-{image['app']}:{context['commit']}"
                         for image in images))
    verified = (deployment is not None and deployment.get("verified") is True
                and deployment.get("configured") is True and deployment.get("target") == "staging"
                and all(deployment.get(key) == "success" for key in ("restart", "smoke", "record_live_version"))
                and deployment.get("rollback") == "skipped" and stage(needs, "deploy-staging") == "success")
    secret_outcome = needs.get("secret-scan", {}).get("outputs", {}).get("scan_outcome") or "unknown"
    complete = (context["ref"] == "refs/heads/main" and published and verified
                and all(stage(needs, job) == "success" for job in ("backend-test", "frontend-build", "docker-build", "secret-scan", "dependency-scan"))
                and secret_outcome in ("success", "failure")
                and all(scan["status"] in ("clean_in_scope", "findings") for scan in scans.values()))
    evidence = {"context": context, "jobs": needs, "scan_policy": {"mode": "advisory", "severity": ["HIGH", "CRITICAL"],
                 "ignore_unfixed": True, "scanners": ["vuln"]}, "scans": scans, "images": images,
                "secret_scan_step_outcome": secret_outcome,
                "publication_verified": published, "staging": deployment, "staging_verified": verified,
                "complete_staging_chain_evidence": complete,
                "limitations": ["Green advisory jobs are not proof of vulnerability-free images.",
                                "Digest identifies the published image, not the currently running remote container.",
                                "Staging verification is a health smoke test, not full transaction acceptance.",
                                "Production approval is independent; this bundle does not claim production deployment."]}
    write_json(directory / "release-evidence.json", evidence)
    lines = ["# Release evidence", "", f"Run: {context['run_url']}", f"Commit: `{context['commit']}`",
             f"Attempt: {context['run_attempt']} · Event: {context['event']} · Ref: `{context['ref']}`", "",
             f"Complete staging-chain evidence: **{'YES' if complete else 'NO — incomplete, failed, skipped or not a main release'}**", "",
             "| Stage | Recorded result |", "|---|---|"]
    for job in ("backend-test", "frontend-build", "secret-scan", "dependency-scan", "docker-build", "deploy-staging"):
        lines.append(f"| {job} | {stage(needs, job)} |")
    lines += ["", f"Secret scan step outcome (advisory): **{evidence['secret_scan_step_outcome']}**; no secret report is included."]
    lines += ["", "| Vulnerability scan (advisory) | Result | HIGH | CRITICAL |", "|---|---|---:|---:|"]
    for name, scan in scans.items():
        lines.append(f"| {name} | {scan['status']} (step: {scan['step_outcome']}) | {scan['high'] if scan['high'] is not None else 'unknown'} | {scan['critical'] if scan['critical'] is not None else 'unknown'} |")
    lines += ["", "Scope: HIGH/CRITICAL, fixable vulnerabilities only (`ignore-unfixed=true`); findings are advisory.",
              "", "## Published images (registry manifest digests, not local image IDs)", ""]
    if not images:
        lines.append("No verified image metadata retained for this run/attempt.")
    for image in images:
        lines.append(f"- {image['app']}: `{image['reference']}` → `{image.get('registry_digest') or 'not recorded'}` ({image['publication']})")
    lines += ["", "## Staging", "", f"Verified deployment + health smoke: **{'YES' if verified else 'NO / not evidenced'}**"]
    if deployment:
        lines += [f"Configured: {deployment.get('configured', 'unknown')}; restart: {deployment.get('restart', 'unknown')}; smoke: {deployment.get('smoke', 'unknown')}; "
                  f"live-version record: {deployment.get('record_live_version', 'unknown')}; rollback: {deployment.get('rollback', 'unknown')}."]
    lines += ["", "## Limitations", ""] + [f"- {limitation}" for limitation in evidence["limitations"]]
    (directory / "release-evidence.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    slide = ["# Pipeline evidence — slide excerpt", "", f"Source: {context['run_url']}",
             f"Commit: `{context['commit']}` · attempt {context['run_attempt']}", "",
             f"Tests: backend {stage(needs, 'backend-test')}; frontend {stage(needs, 'frontend-build')}",
             "Scans (advisory): " + "; ".join(f"{name} {scan['status']} H={scan['high']} C={scan['critical']}" for name, scan in scans.items()),
             f"GHCR digest evidence: {'verified' if published else 'NOT VERIFIED'}",
             f"Staging health verification: {'verified' if verified else 'NOT VERIFIED'}",
             f"Complete retained chain: {'YES' if complete else 'NO'}", ""]
    slide += [f"- {image['app']}: `{image.get('immutable_reference') or 'no verified digest'}`" for image in images]
    slide += ["", "No production, zero-vulnerability or transaction-acceptance claim is made."]
    (directory / "slide-excerpt.md").write_text("\n".join(slide) + "\n", encoding="utf-8")
    return evidence


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("images", "deployment", "summary"))
    parser.add_argument("--directory", required=True, type=Path)
    parser.add_argument("--backend-build", default="skipped")
    parser.add_argument("--frontend-build", default="skipped")
    parser.add_argument("--publish", default="skipped")
    parser.add_argument("--configured", default="false")
    for name in ("restart", "smoke", "record", "rollback"):
        parser.add_argument("--" + name, default="skipped")
    args = parser.parse_args()
    context = context_from_env()
    if args.mode == "images":
        return 0 if capture_images(args.directory, context,
                                   {"backend": args.backend_build, "frontend": args.frontend_build}, args.publish) else 1
    if args.mode == "deployment":
        capture_deployment(args.directory, context, args.configured, args.restart, args.smoke, args.record, args.rollback)
    else:
        summarize(args.directory, context, json.loads(os.environ.get("NEEDS_JSON", "{}")))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
