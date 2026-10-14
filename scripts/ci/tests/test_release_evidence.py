import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import release_evidence as evidence
import export_release_evidence as exporter

SHA = "a" * 40
DIGEST = "sha256:" + "b" * 64
CONTEXT = {"repository": "NUS-SE-34FT-GROUP4/c2cTradePlatform", "commit": SHA, "run_id": "123",
           "run_attempt": "2", "ref": "refs/heads/main", "event": "push", "run_url": "https://github.com/example/run/123"}


class EvidenceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.context = dict(CONTEXT)
        self.needs = {job: {"result": "success", "outputs": {}} for job in
                      ("backend-test", "frontend-build", "secret-scan", "dependency-scan", "docker-build", "deploy-staging")}
        self.needs["dependency-scan"]["outputs"]["scan_outcome"] = "success"
        self.needs["secret-scan"]["outputs"]["scan_outcome"] = "success"
        self.needs["docker-build"]["outputs"].update(backend_scan_outcome="success", frontend_scan_outcome="success")

    def report(self, name="trivy-repository.json", severities=()):
        evidence.write_json(self.directory / name, {"SchemaVersion": 2, "ArtifactName": "test-fixture", "ArtifactType": "filesystem", "Results": [{
            "Vulnerabilities": [{"Severity": severity} for severity in severities]}]})

    def successful_release(self):
        for filename in evidence.SCANS.values():
            self.report(filename)
        with patch.object(evidence, "command", side_effect=lambda *args: json.dumps({"digest": DIGEST})
                          if "imagetools" in args else "sha256:" + "c" * 64):
            self.assertTrue(evidence.capture_images(self.directory, self.context,
                                                    {"backend": "success", "frontend": "success"}, "success"))
        evidence.capture_deployment(self.directory, self.context, "true", "success", "success", "success", "skipped")

    def test_complete_chain_and_slide_export(self):
        self.successful_release()
        result = evidence.summarize(self.directory, self.context, self.needs)
        self.assertTrue(result["complete_staging_chain_evidence"])
        self.assertTrue(result["publication_verified"])
        self.assertIn(DIGEST, (self.directory / "slide-excerpt.md").read_text())
        self.assertIn("Complete retained chain: YES", (self.directory / "slide-excerpt.md").read_text())

    def test_green_jobs_without_artifacts_never_mean_clean_or_deployed(self):
        result = evidence.summarize(self.directory, self.context, self.needs)
        self.assertFalse(result["complete_staging_chain_evidence"])
        self.assertFalse(result["publication_verified"])
        self.assertFalse(result["staging_verified"])
        self.assertEqual("not_retained", result["scans"]["backend"]["status"])
        self.assertIsNone(result["scans"]["backend"]["critical"])

    def test_scanner_failure_with_findings_is_not_a_clean_scan(self):
        self.report(severities=("HIGH", "HIGH", "CRITICAL", "LOW"))
        result = evidence.scan_result(self.directory / "trivy-repository.json", "failure")
        self.assertEqual("findings", result["status"])
        self.assertEqual((2, 1), (result["high"], result["critical"]))

    def test_failed_scanner_with_zero_findings_is_an_error(self):
        self.report()
        self.assertEqual("scanner_error", evidence.scan_result(self.directory / "trivy-repository.json", "failure")["status"])

    def test_missing_corrupt_and_invalid_scan_reports_stay_unknown(self):
        path = self.directory / "scan.json"
        for value in (None, "invalid", "{}", '{"SchemaVersion":2}', '{"SchemaVersion":2,"Results":{}}',
                      '{"SchemaVersion":2,"Results":[{"Vulnerabilities":[null]}]}'):
            with self.subTest(value=value):
                if value is not None:
                    path.write_text(value)
                result = evidence.scan_result(path, "success")
                self.assertEqual("not_retained", result["status"])
                self.assertIsNone(result["high"])

    def test_null_vulnerabilities_is_valid_clean_in_scope(self):
        evidence.write_json(self.directory / "scan.json", {"SchemaVersion": 2, "ArtifactName": "test", "ArtifactType": "filesystem", "Results": [{"Vulnerabilities": None}]})
        self.assertEqual("clean_in_scope", evidence.scan_result(self.directory / "scan.json", "success")["status"])

    def test_valid_report_with_unknown_outcome_does_not_claim_completion(self):
        self.report()
        self.assertEqual("outcome_unknown", evidence.scan_result(self.directory / "trivy-repository.json", None)["status"])

    def test_pr_images_are_not_published_even_when_locally_built(self):
        self.context["ref"] = "refs/pull/4/merge"
        with patch.object(evidence, "command", return_value="sha256:local") as docker:
            evidence.capture_images(self.directory, self.context, {"backend": "success", "frontend": "success"}, "skipped")
        self.assertEqual(2, docker.call_count)
        images = evidence.read_json(self.directory / "images.json")["images"]
        self.assertTrue(all(image["publication"] == "not_requested" and image["registry_digest"] is None for image in images))

    def test_registry_digest_is_not_local_image_id_and_owner_is_lowercase(self):
        self.successful_release()
        image = evidence.read_json(self.directory / "images.json")["images"][0]
        self.assertNotEqual(image["local_image_id"], image["registry_digest"])
        self.assertTrue(image["reference"].startswith("ghcr.io/nus-se-34ft-group4/"))
        self.assertTrue(image["reference"].endswith(SHA))
        self.assertTrue(image["immutable_reference"].endswith("@" + DIGEST))

    def test_failed_publish_is_not_relabelled_as_success(self):
        with patch.object(evidence, "command", return_value="sha256:local") as docker:
            evidence.capture_images(self.directory, self.context, {"backend": "success", "frontend": "success"}, "failure")
        self.assertEqual(2, docker.call_count)
        self.assertTrue(all(image["publication"] == "not_verified" for image in evidence.read_json(self.directory / "images.json")["images"]))

    def test_digest_capture_errors_are_recorded_and_fail_the_capture(self):
        for manifest in ("not-json", '{"digest":"local-image-id"}', "null"):
            with self.subTest(manifest=manifest), patch.object(evidence, "command", return_value=manifest):
                self.assertFalse(evidence.capture_images(self.directory, self.context, {"backend": "success", "frontend": "success"}, "success"))
                self.assertEqual("digest_capture_failed", evidence.read_json(self.directory / "images.json")["images"][0]["publication"])

    def test_docker_errors_do_not_leak_raw_exception_output(self):
        error = subprocess.CalledProcessError(1, "docker", stderr="SECRET_SENTINEL")
        with patch.object(evidence, "command", side_effect=error):
            self.assertFalse(evidence.capture_images(self.directory, self.context, {"backend": "success"}, "success"))
        self.assertNotIn("SECRET_SENTINEL", (self.directory / "images.json").read_text())

    def test_unbuilt_images_do_not_invoke_docker(self):
        with patch.object(evidence, "command") as docker:
            evidence.capture_images(self.directory, self.context, {"backend": "failure", "frontend": "skipped"}, "skipped")
        docker.assert_not_called()

    def test_deployment_requires_configuration_restart_smoke_and_live_record(self):
        for configured, restart, smoke, record, rollback in (
                ("false", "skipped", "skipped", "skipped", "skipped"),
                ("true", "failure", "skipped", "skipped", "success"),
                ("true", "success", "failure", "skipped", "success"),
                ("true", "success", "success", "failure", "skipped"),
                ("true", "success", "success", "success", "failure")):
            with self.subTest(smoke=smoke, record=record):
                result = evidence.capture_deployment(self.directory, self.context, configured, restart, smoke, record, rollback)
                self.assertFalse(result["verified"])

    def test_advisory_findings_are_retained_without_turning_policy_blocking(self):
        self.successful_release()
        self.report("trivy-backend.json", ("CRITICAL",))
        self.needs["docker-build"]["outputs"]["backend_scan_outcome"] = "failure"
        result = evidence.summarize(self.directory, self.context, self.needs)
        self.assertTrue(result["complete_staging_chain_evidence"])
        self.assertEqual("advisory", result["scan_policy"]["mode"])
        self.assertEqual("findings", result["scans"]["backend"]["status"])

    def test_failed_jobs_prevent_a_complete_chain(self):
        self.successful_release()
        for job in ("backend-test", "frontend-build", "docker-build", "deploy-staging", "dependency-scan", "secret-scan"):
            with self.subTest(job=job):
                self.needs[job]["result"] = "failure"
                self.assertFalse(evidence.summarize(self.directory, self.context, self.needs)["complete_staging_chain_evidence"])
                self.needs[job]["result"] = "success"

    def test_cross_commit_run_and_attempt_metadata_are_rejected(self):
        self.successful_release()
        for key in ("commit", "repository", "run_id", "run_attempt"):
            context = {**self.context, key: "other"}
            with self.subTest(key=key):
                result = evidence.summarize(self.directory, context, self.needs)
                self.assertFalse(result["publication_verified"])
                self.assertFalse(result["staging_verified"])

    def test_malformed_context_is_not_matching_evidence(self):
        self.assertFalse(evidence.same_run({"context": "invalid"}, self.context))

    def test_malformed_image_metadata_is_not_verified(self):
        for images in (None, "invalid", [None], [{"app": "backend"}]):
            with self.subTest(images=images):
                evidence.write_json(self.directory / "images.json", {"context": self.context, "publish_step": "success", "images": images})
                self.assertFalse(evidence.summarize(self.directory, self.context, self.needs)["publication_verified"])

    def test_registry_reference_must_match_the_recorded_commit(self):
        self.successful_release()
        release = evidence.read_json(self.directory / "images.json")
        release["images"][0]["reference"] = release["images"][0]["reference"].replace(SHA, "other-commit")
        evidence.write_json(self.directory / "images.json", release)
        self.assertFalse(evidence.summarize(self.directory, self.context, self.needs)["publication_verified"])

    def test_environment_metadata_does_not_copy_secrets(self):
        with patch.dict(os.environ, {"GITHUB_REPOSITORY": CONTEXT["repository"], "GITHUB_RUN_ID": "123", "TOKEN": "PRIVATE_SENTINEL"}):
            context = evidence.context_from_env()
        self.assertNotIn("PRIVATE_SENTINEL", json.dumps(context))
        self.assertIn("/actions/runs/123", context["run_url"])

    def test_skipped_secret_scan_prevents_a_complete_chain(self):
        self.successful_release()
        self.needs["secret-scan"]["outputs"]["scan_outcome"] = "skipped"
        self.assertFalse(evidence.summarize(self.directory, self.context, self.needs)["complete_staging_chain_evidence"])

    def test_incomplete_deployment_metadata_is_not_verified(self):
        evidence.write_json(self.directory / "staging-deployment.json", {"context": self.context, "verified": True})
        self.assertFalse(evidence.summarize(self.directory, self.context, self.needs)["staging_verified"])


class ExportTests(unittest.TestCase):
    def run_data(self):
        return {"id": 123, "run_attempt": 2, "head_sha": SHA, "head_branch": "main", "event": "push", "status": "completed",
                "conclusion": "success", "html_url": CONTEXT["run_url"], "updated_at": "2026-10-13T00:00:00Z"}

    def test_missing_or_skipped_steps_do_not_prove_deployment(self):
        text = exporter.observed_summary(self.run_data(), [{"name": "Deploy to staging", "steps": [
            {"name": "Smoke test", "conclusion": "skipped"}]}], "2")
        self.assertIn("chain: **NO**", text)
        self.assertIn("skipped", text)

    def test_historical_export_preserves_unknown_reports_and_digest(self):
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp) / "output"
            job = {"name": "Deploy to staging", "conclusion": "success", "steps": []}
            with patch.object(exporter, "command", side_effect=[json.dumps(self.run_data()), json.dumps(self.run_data()), json.dumps([{"jobs": [job]}])]), \
                    patch.object(exporter.subprocess, "run", return_value=subprocess.CompletedProcess([], 1)):
                exporter.export(CONTEXT["repository"], 123, None, directory)
            result = evidence.read_json(directory / "release-evidence.json")
            self.assertFalse(result["complete_staging_chain_evidence"])
            self.assertFalse(result["publication_verified"])
            self.assertEqual("not_retained", result["scans"]["repository"]["status"])
            self.assertTrue((directory / "observed-pipeline.md").exists())

    def test_export_uses_attempt_specific_jobs_and_artifact_name(self):
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp) / "output"
            with patch.object(exporter, "command", side_effect=[json.dumps(self.run_data()), json.dumps(self.run_data()), '[{"jobs":[]}]']) as gh, \
                    patch.object(exporter.subprocess, "run", return_value=subprocess.CompletedProcess([], 1)) as download:
                exporter.export(CONTEXT["repository"], 123, 1, directory)
            self.assertIn("attempts/1", gh.call_args_list[1].args[-1])
            self.assertIn("attempts/1/jobs", gh.call_args_list[2].args[-1])
            self.assertIn("release-evidence-1", download.call_args.args[0])

    def test_existing_output_is_not_overwritten(self):
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            (directory / "keep.txt").write_text("existing")
            with self.assertRaises(ValueError):
                exporter.export(CONTEXT["repository"], 123, 1, directory)
            self.assertEqual("existing", (directory / "keep.txt").read_text())

    def test_export_preserves_retained_scan_outcomes_instead_of_green_api_jobs(self):
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp) / "output"
            retained = {"context": CONTEXT, "complete_staging_chain_evidence": True,
                        "scans": {"backend": {"step_outcome": "failure", "status": "findings", "critical": 1}}}

            def download(*args, **kwargs):
                evidence.write_json(directory / "retained" / "release-evidence.json", retained)
                (directory / "retained" / "slide-excerpt.md").write_text("advisory finding")
                return subprocess.CompletedProcess([], 0)

            with patch.object(exporter, "command", side_effect=[json.dumps(self.run_data()), json.dumps(self.run_data()), '[{"jobs":[]}]']), \
                    patch.object(exporter.subprocess, "run", side_effect=download):
                exporter.export(CONTEXT["repository"], 123, 2, directory)
            self.assertEqual("failure", evidence.read_json(directory / "release-evidence.json")["scans"]["backend"]["step_outcome"])
            self.assertEqual("advisory finding", (directory / "slide-excerpt.md").read_text())


if __name__ == "__main__":
    unittest.main()
