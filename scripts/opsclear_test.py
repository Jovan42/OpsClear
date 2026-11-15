#!/usr/bin/env python3
"""Unit tests for scripts/opsclear. No network: HTTP goes through a fake transport.

Run: python3 scripts/opsclear_test.py
"""
import io
import json
import os
import re
import sys
import tempfile
import unittest
from datetime import date
from importlib.machinery import SourceFileLoader
from importlib.util import module_from_spec, spec_from_loader
from urllib.parse import urlsplit

_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "opsclear")
_loader = SourceFileLoader("opsclear", _path)
_spec = spec_from_loader("opsclear", _loader)
opsclear = module_from_spec(_spec)
sys.modules["opsclear"] = opsclear  # dataclasses look the module up while loading
_loader.exec_module(opsclear)

KEY = "opck_secret_test_key_123"
TODAY = date(2026, 11, 5)
URL = "https://opsclear.test"

PRJ_OLD = {"id": "p1", "friendlyId": "PRJ-001", "name": "Maintenance", "status": "ACTIVE"}
PRJ_NEW = {"id": "p13", "friendlyId": "PRJ-013", "name": "Dev Workflow", "status": "ACTIVE"}
PRJ_DONE = {"id": "p7", "friendlyId": "PRJ-007", "name": "Old phase", "status": "COMPLETED"}
MILESTONES = [{"id": "m38", "friendlyId": "MIL-038", "name": "Agents"}]
MEMBERS = [{"userId": "u1", "userName": "Jovan M", "userEmail": "jovan@example.com"}]


def job(fid, **kw):
    base = {
        "id": f"id-{fid}", "friendlyId": fid, "projectId": "p13", "title": f"Title {fid}",
        "description": "keep me", "client": "ACME", "assignedTo": "u1", "assignedToName": "Jovan M",
        "deadline": "2026-11-11T00:00:00Z", "status": "NEW", "priority": "LOW", "milestoneId": "m38",
        "milestoneName": "Agents", "typeId": "t1", "typeName": "Chore", "relationships": [], "links": [],
    }
    base.update(kw)
    return base


class FakeApi:
    """Routes (METHOD, path) to (status, body); unknown routes return 404. Records every call."""

    def __init__(self, routes):
        self.routes = routes
        self.calls = []

    def __call__(self, method, url, headers, data):
        parts = urlsplit(url)
        body = json.loads(data) if data else None
        self.calls.append({"method": method, "path": parts.path, "query": parts.query,
                           "headers": headers, "body": body})
        status, payload = self.routes.get((method, parts.path), (404, {"message": "Job not found"}))
        raw = b"" if payload is None else json.dumps(payload).encode()
        return opsclear.Response(status, raw)

    def writes(self):
        return [c for c in self.calls if c["method"] != "GET"]


def run(argv, routes=None, env=None):
    fake = FakeApi(routes or {})
    out, err = io.StringIO(), io.StringIO()
    environment = {"OPSCLEAR_API_KEY": KEY, "OPSCLEAR_URL": URL} if env is None else env
    code = opsclear.main(argv, env=environment, transport=fake, stdout=out, stderr=err, today=TODAY)
    return code, out.getvalue(), err.getvalue(), fake


def job_routes(j, project=PRJ_NEW):
    path = f"/api/projects/{project['friendlyId']}/jobs/{j['friendlyId']}"
    return {
        ("GET", "/api/projects"): (200, [PRJ_OLD, PRJ_DONE, PRJ_NEW]),
        ("GET", f"/api/projects/{project['friendlyId']}"): (200, project),
        ("GET", path): (200, j),
        ("GET", f"/api/projects/{project['friendlyId']}/milestones"): (200, MILESTONES),
        ("GET", f"/api/projects/{project['friendlyId']}/members"): (200, MEMBERS),
    }


class ArgumentParsingTest(unittest.TestCase):
    def parse(self, argv):
        return opsclear.build_parser().parse_args(argv)

    def test_friendly_ids_are_normalised(self):
        args = self.parse(["job", "get", "job-7"])
        self.assertEqual(args.job, "JOB-7")
        self.assertEqual(self.parse(["job", "list", "13"]).project, "PRJ-013")

    def test_wrong_kind_of_id_is_rejected(self):
        code, _, err, _ = run(["job", "get", "PRJ-013"])
        self.assertEqual(code, opsclear.EXIT_USAGE)
        self.assertIn("JOB-123", err)

    def test_status_and_priority_choices(self):
        args = self.parse(["job", "status", "JOB-1", "in-progress"])
        self.assertEqual(args.status, "IN_PROGRESS")
        code, _, _, _ = run(["job", "status", "JOB-1", "DONE"])
        self.assertEqual(code, opsclear.EXIT_USAGE)

    def test_job_list_defaults_to_open(self):
        self.assertEqual(self.parse(["job", "list", "PRJ-013"]).status, "OPEN")

    def test_common_flags_on_every_command(self):
        args = self.parse(["link", "add", "JOB-1", "https://x", "--label", "PR #1", "--json", "--yes", "--dry-run"])
        self.assertTrue(args.json and args.yes and args.dry_run)

    def test_bad_deadline_is_a_usage_error(self):
        code, _, err, fake = run(["job", "update", "JOB-1", "--deadline", "next week"])
        self.assertEqual(code, opsclear.EXIT_USAGE)
        self.assertEqual(fake.calls, [])

    def test_deadline_formats(self):
        self.assertEqual(opsclear.parse_deadline("2026-11-12"), "2026-11-12T00:00:00Z")
        self.assertEqual(opsclear.parse_deadline("2026-11-12T10:00:00+01:00"), "2026-11-12T09:00:00Z")
        self.assertIsNone(opsclear.parse_deadline("none"))

    def test_missing_key_is_a_clear_error(self):
        code, _, err, fake = run(["project", "list"], env={})
        self.assertEqual(code, opsclear.EXIT_ERROR)
        self.assertIn("OPSCLEAR_API_KEY is not set", err)
        self.assertEqual(fake.calls, [])

    def test_key_is_read_from_key_file_when_env_is_unset(self):
        with tempfile.TemporaryDirectory() as home:
            os.makedirs(os.path.join(home, ".config", "opsclear"))
            with open(os.path.join(home, ".config", "opsclear", "api-key"), "w") as f:
                f.write(KEY + "\n")
            self.assertEqual(opsclear.load_key({"HOME": home}), KEY)
            self.assertEqual(opsclear.load_key({"HOME": home, "OPSCLEAR_API_KEY": "opck_env"}), "opck_env")

    def test_key_file_override(self):
        with tempfile.TemporaryDirectory() as d:
            path = os.path.join(d, "key")
            with open(path, "w") as f:
                f.write(KEY)
            self.assertEqual(opsclear.load_key({"OPSCLEAR_KEY_FILE": path}), KEY)
            self.assertEqual(opsclear.load_key({"OPSCLEAR_KEY_FILE": os.path.join(d, "missing")}), "")


class TemplateFillingTest(unittest.TestCase):
    TEMPLATE = {"title": "Backend:", "priority": "HIGH", "client": None, "assigneeMode": "ASK",
                "description": "## Goal\n<!-- x -->\n\n## Scope\n", "deadlineOffsetDays": None}

    def test_title_prefix_added_once(self):
        fields, _ = opsclear.fill_from_template(self.TEMPLATE, title="org CRUD", description=None, today=TODAY)
        self.assertEqual(fields["title"], "Backend: org CRUD")
        fields, _ = opsclear.fill_from_template(self.TEMPLATE, title="backend: org CRUD",
                                                description=None, today=TODAY)
        self.assertEqual(fields["title"], "backend: org CRUD")

    def test_numbered_layer_titles_keep_their_prefix(self):
        fields, _ = opsclear.fill_from_template(self.TEMPLATE, title="Backend 2: project stats endpoint",
                                                description=None, today=TODAY)
        self.assertEqual(fields["title"], "Backend 2: project stats endpoint")
        fields, _ = opsclear.fill_from_template(self.TEMPLATE, title="Backend2 stats", description=None,
                                                today=TODAY)
        self.assertEqual(fields["title"], "Backend: Backend2 stats")
        tech_debt = dict(self.TEMPLATE, title="Tech debt: ")
        fields, _ = opsclear.fill_from_template(tech_debt, title="Tech debt 2: split service",
                                                description=None, today=TODAY)
        self.assertEqual(fields["title"], "Tech debt 2: split service")

    def test_defaults_come_from_template(self):
        tpl = dict(self.TEMPLATE, assigneeMode="FIXED", assigneeId="u9", deadlineOffsetDays=3, client="ACME")
        fields, _ = opsclear.fill_from_template(tpl, title="x", description=None, today=TODAY)
        self.assertEqual(fields["priority"], "HIGH")
        self.assertEqual(fields["assignedTo"], "u9")
        self.assertEqual(fields["deadline"], "2026-11-08T00:00:00Z")
        self.assertEqual(fields["client"], "ACME")
        self.assertEqual(fields["description"], self.TEMPLATE["description"])

    def test_warns_about_missing_headings(self):
        _, warnings = opsclear.fill_from_template(self.TEMPLATE, title="x", description="## Goal\ntext",
                                                  today=TODAY)
        self.assertEqual(len(warnings), 1)
        self.assertIn("## Scope", warnings[0])
        _, warnings = opsclear.fill_from_template(self.TEMPLATE, title="x", description="## Goal\n## Scope\n",
                                                  today=TODAY)
        self.assertEqual(warnings, [])

    def test_find_template_prefers_project_scope_and_accepts_ids(self):
        templates = [{"id": "o", "friendlyId": "TPL-001", "name": "Chore", "scope": "ORG"},
                     {"id": "p", "friendlyId": "TPL-007", "name": "Chore", "scope": "PROJECT"}]
        self.assertEqual(opsclear.find_template(templates, "chore", "PRJ-1")["id"], "p")
        self.assertEqual(opsclear.find_template(templates, "tpl-001", "PRJ-1")["id"], "o")
        with self.assertRaises(opsclear.CliError):
            opsclear.find_template(templates, "Nope", "PRJ-1")


class JobCreateTest(unittest.TestCase):
    def routes(self):
        return {
            ("GET", "/api/projects/PRJ-013/templates"): (200, [{
                "id": "tpl", "friendlyId": "TPL-003", "name": "Backend feature", "scope": "ORG",
                "title": "Backend:", "priority": "MEDIUM", "assigneeMode": "ASK",
                "defaultTypeName": "Backend", "description": "## Goal\n"}]),
            ("GET", "/api/projects/PRJ-013/job-types"): (200, [{"id": "type-be", "name": "backend"}]),
            ("GET", "/api/projects/PRJ-013/milestones"): (200, MILESTONES),
            ("GET", "/api/projects/PRJ-013/members"): (200, MEMBERS),
            ("POST", "/api/projects/PRJ-013/jobs"): (201, job("JOB-400", title="Backend: x")),
            ("POST", "/api/projects/PRJ-013/templates/tpl/use"): (200, {"resolvedTypeId": "type-be"}),
        }

    ARGV = ["job", "create", "PRJ-013", "--template", "Backend feature", "--title", "x",
            "--milestone", "MIL-038", "--deadline", "2026-11-12", "--assignee", "jovan@example.com"]

    def test_without_yes_prints_plan_and_creates_nothing(self):
        code, out, _, fake = run(self.ARGV, self.routes())
        self.assertEqual(code, opsclear.EXIT_NEEDS_CONFIRM)
        self.assertIn("Plan (not applied)", out)
        self.assertIn("Backend: x", out)
        self.assertEqual(fake.writes(), [])

    def test_with_yes_creates_with_resolved_fields_and_records_usage(self):
        code, out, _, fake = run(self.ARGV + ["--yes"], self.routes())
        self.assertEqual(code, opsclear.EXIT_OK)
        create, use = fake.writes()
        self.assertEqual(create["path"], "/api/projects/PRJ-013/jobs")
        self.assertEqual(create["body"], {
            "title": "Backend: x", "description": "## Goal\n", "priority": "MEDIUM", "milestoneId": "m38",
            "assignedTo": "u1", "deadline": "2026-11-12T00:00:00Z", "typeId": "type-be"})
        self.assertEqual(use["path"], "/api/projects/PRJ-013/templates/tpl/use")
        self.assertIn("Created JOB-400", out)

    def test_dry_run_prints_request_and_sends_nothing(self):
        code, out, _, fake = run(self.ARGV + ["--dry-run", "--yes"], self.routes())
        self.assertEqual(code, opsclear.EXIT_OK)
        self.assertIn("POST /api/projects/PRJ-013/jobs", out)
        self.assertEqual(fake.writes(), [])


class FullPutTest(unittest.TestCase):
    def test_update_body_keeps_every_other_field(self):
        body = opsclear.update_body(job("JOB-1"), {"title": "New"})
        self.assertEqual(body, {"title": "New", "description": "keep me", "client": "ACME", "assignedTo": "u1",
                                "deadline": "2026-11-11T00:00:00Z", "priority": "LOW",
                                "milestoneId": "m38", "typeId": "t1"})

    def test_update_body_rejects_unknown_fields(self):
        with self.assertRaises(ValueError):
            opsclear.update_body(job("JOB-1"), {"status": "COMPLETED"})

    def test_title_change_runs_directly_and_sends_full_job(self):
        j = job("JOB-1")
        routes = job_routes(j)
        routes[("PUT", "/api/projects/PRJ-013/jobs/JOB-1")] = (200, j)
        code, _, _, fake = run(["job", "update", "JOB-1", "--title", "New"], routes)
        self.assertEqual(code, opsclear.EXIT_OK)
        (put,) = fake.writes()
        self.assertEqual(put["body"]["title"], "New")
        self.assertEqual(put["body"]["description"], "keep me")
        self.assertEqual(put["body"]["typeId"], "t1")

    def test_deadline_change_needs_yes(self):
        j = job("JOB-1")
        routes = job_routes(j)
        routes[("PUT", "/api/projects/PRJ-013/jobs/JOB-1")] = (200, j)
        code, out, _, fake = run(["job", "update", "JOB-1", "--deadline", "2026-11-13"], routes)
        self.assertEqual(code, opsclear.EXIT_NEEDS_CONFIRM)
        self.assertIn("deadline: 2026-11-11 -> 2026-11-13", out)
        self.assertEqual(fake.writes(), [])
        code, _, _, fake = run(["job", "update", "JOB-1", "--deadline", "2026-11-13", "--yes"], routes)
        self.assertEqual(code, opsclear.EXIT_OK)
        self.assertEqual(fake.writes()[0]["body"]["deadline"], "2026-11-13T00:00:00Z")
        self.assertEqual(fake.writes()[0]["body"]["client"], "ACME")

    def test_no_op_update_sends_nothing(self):
        code, out, _, fake = run(["job", "update", "JOB-1", "--title", "Title JOB-1"], job_routes(job("JOB-1")))
        self.assertEqual(code, opsclear.EXIT_OK)
        self.assertIn("nothing to change", out)
        self.assertEqual(fake.writes(), [])


class StatusTest(unittest.TestCase):
    def routes(self, j):
        routes = job_routes(j)
        routes[("PATCH", f"/api/projects/PRJ-013/jobs/{j['friendlyId']}/status")] = (200, j)
        return routes

    def test_in_progress_runs_directly(self):
        code, _, _, fake = run(["job", "status", "JOB-1", "IN_PROGRESS"], self.routes(job("JOB-1")))
        self.assertEqual(code, opsclear.EXIT_OK)
        self.assertEqual(fake.writes()[0]["body"], {"status": "IN_PROGRESS"})

    def test_completed_needs_yes(self):
        routes = self.routes(job("JOB-1", status="IN_PROGRESS"))
        code, _, _, fake = run(["job", "status", "JOB-1", "COMPLETED"], routes)
        self.assertEqual(code, opsclear.EXIT_NEEDS_CONFIRM)
        self.assertEqual(fake.writes(), [])

    def test_blocked_needs_reason(self):
        code, _, err, _ = run(["job", "status", "JOB-1", "BLOCKED", "--yes"],
                              self.routes(job("JOB-1", status="IN_PROGRESS")))
        self.assertEqual(code, opsclear.EXIT_ERROR)
        self.assertIn("--reason", err)


class BlockedByDirectionTest(unittest.TestCase):
    def routes(self, blocked):
        routes = job_routes(blocked)
        routes[("GET", "/api/projects/PRJ-013/jobs/JOB-123")] = (200, job("JOB-123"))
        routes[("POST", "/api/projects/PRJ-013/jobs/JOB-124/relationships")] = (201, {"id": "r1"})
        return routes

    def test_relationship_is_posted_on_blocked_job_with_blocker_as_target(self):
        code, _, _, fake = run(["job", "block", "JOB-124", "--by", "JOB-123"], self.routes(job("JOB-124")))
        self.assertEqual(code, opsclear.EXIT_OK)
        (post,) = fake.writes()
        self.assertEqual(post["path"], "/api/projects/PRJ-013/jobs/JOB-124/relationships")
        self.assertEqual(post["body"], {"targetJobId": "id-JOB-123", "type": "BLOCKED_BY"})

    def test_existing_relationship_is_not_duplicated(self):
        rel = {"type": "BLOCKED_BY", "direction": "OUTGOING", "job": {"id": "id-JOB-123"}}
        code, out, _, fake = run(["job", "block", "JOB-124", "--by", "JOB-123"],
                                 self.routes(job("JOB-124", relationships=[rel])))
        self.assertEqual(code, opsclear.EXIT_OK)
        self.assertIn("already blocked", out)
        self.assertEqual(fake.writes(), [])

    def test_blocker_in_other_project_is_an_error(self):
        routes = job_routes(job("JOB-124"))
        code, _, err, fake = run(["job", "block", "JOB-124", "--by", "JOB-999"], routes)
        self.assertEqual(code, opsclear.EXIT_ERROR)
        self.assertIn("same project", err)
        self.assertEqual(fake.writes(), [])

    def test_labels_follow_direction(self):
        self.assertEqual(opsclear.relationship_label({"type": "BLOCKED_BY", "direction": "OUTGOING"}), "Blocked by")
        self.assertEqual(opsclear.relationship_label({"type": "BLOCKED_BY", "direction": "INCOMING"}), "Blocks")


class FriendlyIdLookupTest(unittest.TestCase):
    def test_searches_projects_active_first_newest_first(self):
        routes = {("GET", "/api/projects"): (200, [PRJ_DONE, PRJ_OLD, PRJ_NEW]),
                  ("GET", "/api/projects/PRJ-001/jobs/JOB-5"): (200, job("JOB-5", projectId="p1")),
                  ("GET", "/api/projects/PRJ-001/milestones"): (200, [])}
        code, out, _, fake = run(["job", "get", "JOB-5"], routes)
        self.assertEqual(code, opsclear.EXIT_OK)
        self.assertIn("Project:   PRJ-001", out)
        tried = [c["path"] for c in fake.calls if "/jobs/" in c["path"]]
        self.assertEqual(tried, ["/api/projects/PRJ-013/jobs/JOB-5", "/api/projects/PRJ-001/jobs/JOB-5"])
        self.assertEqual(fake.calls[0]["query"], "status=ALL")

    def test_not_found_anywhere(self):
        code, _, err, _ = run(["job", "get", "JOB-5"], {("GET", "/api/projects"): (200, [PRJ_NEW])})
        self.assertEqual(code, opsclear.EXIT_ERROR)
        self.assertIn("not found in any project", err)

    def test_project_option_skips_search(self):
        code, _, _, fake = run(["job", "get", "JOB-1", "--project", "PRJ-013"], job_routes(job("JOB-1")))
        self.assertEqual(code, opsclear.EXIT_OK)
        self.assertNotIn("/api/projects", [c["path"] for c in fake.calls])

    def test_json_output_includes_project_and_milestone_ids(self):
        code, out, _, _ = run(["job", "get", "JOB-1", "--json"], job_routes(job("JOB-1")))
        data = json.loads(out)
        self.assertEqual(data["project"]["friendlyId"], "PRJ-013")
        self.assertEqual(data["milestoneFriendlyId"], "MIL-038")

    def test_list_filters_by_milestone_and_status(self):
        jobs = [job("JOB-2", status="COMPLETED"), job("JOB-1")]
        routes = {("GET", "/api/projects/PRJ-013/milestones"): (200, MILESTONES),
                  ("GET", "/api/projects/PRJ-013/jobs"): (200, jobs)}
        code, out, _, fake = run(["job", "list", "PRJ-013", "--milestone", "MIL-038"], routes)
        self.assertEqual(code, opsclear.EXIT_OK)
        self.assertIn("JOB-1", out)
        self.assertNotIn("JOB-2", out)
        self.assertEqual(fake.calls[-1]["query"], "milestoneId=m38")
        _, out, _, _ = run(["job", "list", "PRJ-013", "--status", "ALL"], routes)
        self.assertIn("JOB-2", out)


class LinkAndNoteTest(unittest.TestCase):
    URL_PR = "https://github.com/o/r/pull/480"

    def test_link_merged_relabels(self):
        j = job("JOB-1", links=[{"id": "l1", "url": self.URL_PR, "label": "PR #480"}])
        routes = job_routes(j)
        routes[("PUT", "/api/projects/PRJ-013/jobs/JOB-1/links/l1")] = (200, {})
        code, _, _, fake = run(["link", "merged", "JOB-1", self.URL_PR + "/"], routes)
        self.assertEqual(code, opsclear.EXIT_OK)
        self.assertEqual(fake.writes()[0]["body"], {"url": self.URL_PR, "label": "PR #480 (merged)"})

    def test_link_merged_is_idempotent(self):
        self.assertEqual(opsclear.merged_label("PR #480 (merged)"), "PR #480 (merged)")
        self.assertEqual(len(opsclear.merged_label("x" * 100)), 100)

    def test_link_add_skips_existing_url(self):
        j = job("JOB-1", links=[{"id": "l1", "url": self.URL_PR, "label": "PR #480"}])
        code, out, _, fake = run(["link", "add", "JOB-1", self.URL_PR, "--label", "PR #480"], job_routes(j))
        self.assertEqual(code, opsclear.EXIT_OK)
        self.assertIn("already links", out)
        self.assertEqual(fake.writes(), [])

    def test_note_list(self):
        routes = job_routes(job("JOB-1"))
        routes[("GET", "/api/projects/PRJ-013/jobs/JOB-1/notes")] = (200, [
            {"id": "n1", "content": "Promoted to PRJ-013", "createdAt": "2026-11-05T10:00:00Z"}])
        code, out, _, fake = run(["note", "list", "JOB-1"], routes)
        self.assertEqual(code, opsclear.EXIT_OK)
        self.assertIn("--- 2026-11-05", out)
        self.assertIn("Promoted to PRJ-013", out)
        self.assertEqual(fake.writes(), [])

    def test_note_needs_yes(self):
        routes = job_routes(job("JOB-1"))
        routes[("POST", "/api/projects/PRJ-013/jobs/JOB-1/notes")] = (201, {"id": "n1"})
        code, out, _, fake = run(["note", "add", "JOB-1", "--text", "Closed by PR #480"], routes)
        self.assertEqual(code, opsclear.EXIT_NEEDS_CONFIRM)
        self.assertIn("| Closed by PR #480", out)
        self.assertEqual(fake.writes(), [])
        code, _, _, fake = run(["note", "add", "JOB-1", "--text", "Closed by PR #480", "--yes"], routes)
        self.assertEqual(code, opsclear.EXIT_OK)
        self.assertEqual(fake.writes()[0]["body"], {"content": "Closed by PR #480"})


class KeyNeverPrintedTest(unittest.TestCase):
    def test_key_is_sent_only_as_header(self):
        _, out, err, fake = run(["job", "get", "JOB-1", "--project", "PRJ-013"], job_routes(job("JOB-1")))
        self.assertTrue(all(c["headers"]["X-Api-Key"] == KEY for c in fake.calls))
        self.assertTrue(all(KEY not in c["path"] + c["query"] for c in fake.calls))

    def test_dry_run_and_plan_never_show_key(self):
        routes = job_routes(job("JOB-1"))
        for extra in (["--dry-run"], ["--dry-run", "--json"], [], ["--json"]):
            _, out, err, _ = run(["note", "add", "JOB-1", "--text", "hi"] + extra, routes)
            self.assertNotIn(KEY, out + err)

    def test_key_echoed_by_server_is_redacted(self):
        routes = {("GET", "/api/projects"): (500, {"message": f"bad header {KEY}"})}
        code, out, err, _ = run(["project", "list"], routes)
        self.assertEqual(code, opsclear.EXIT_ERROR)
        self.assertNotIn(KEY, out + err)
        self.assertIn("***", err)

    def test_401_is_a_clear_error(self):
        code, _, err, _ = run(["project", "list"], {("GET", "/api/projects"): (401, None)})
        self.assertEqual(code, opsclear.EXIT_ERROR)
        self.assertIn("rejected the API key", err)
        self.assertNotIn(KEY, err)


class PrSyncParsingTest(unittest.TestCase):
    def refs(self, branch="", title="", body=""):
        return opsclear.parse_pr_refs(branch, title, body)

    def test_branch_and_title_refs_are_normalised(self):
        refs, closes = self.refs("feature/job-294-adr", "docs(adr): x (JOB-295)")
        self.assertEqual(refs, ["JOB-294", "JOB-295"])
        self.assertEqual(closes, [])

    def test_closes_and_part_of_lines(self):
        body = "Summary\n\nCloses JOB-1, job-2\n  part of JOB-3\nsee JOB-4\n"
        refs, closes = self.refs(body=body)
        self.assertEqual(refs, ["JOB-1", "JOB-2", "JOB-3"])
        self.assertEqual(closes, ["JOB-1", "JOB-2"])

    def test_closes_on_separate_lines_and_dedup(self):
        refs, closes = self.refs("chore/JOB-1-x", "t (JOB-1)", "Closes JOB-1\nCloses JOB-2\n")
        self.assertEqual(refs, ["JOB-1", "JOB-2"])
        self.assertEqual(closes, ["JOB-1", "JOB-2"])

    def test_mid_line_mention_is_ignored(self):
        refs, closes = self.refs(body="This also Closes JOB-9 maybe\nrelated: JOB-8")
        self.assertEqual((refs, closes), ([], []))

    def test_html_comments_are_ignored(self):
        body = "<!-- Closes JOB-1 or\nPart of JOB-2 -->\nCloses JOB-3 <!-- x -->\n"
        refs, closes = self.refs(body=body)
        self.assertEqual(refs, ["JOB-3"])
        self.assertEqual(closes, ["JOB-3"])

    def test_none_body(self):
        self.assertEqual(self.refs(body=None), ([], []))

    def test_closes_wins_when_both_keywords_name_a_job(self):
        refs, closes = self.refs(body="Part of JOB-1\nCloses JOB-1")
        self.assertEqual((refs, closes), (["JOB-1"], ["JOB-1"]))


class PrLabelTest(unittest.TestCase):
    def test_closed_label(self):
        self.assertEqual(opsclear.closed_label("PR #1: x"), "PR #1: x (closed)")
        self.assertEqual(opsclear.closed_label("PR #1: x (closed)"), "PR #1: x (closed)")
        self.assertEqual(opsclear.closed_label("PR #1 (merged)"), "PR #1 (merged)")
        self.assertEqual(len(opsclear.closed_label("x" * 100)), 100)
        self.assertTrue(opsclear.closed_label("x" * 100).endswith(" (closed)"))

    def test_base_label(self):
        self.assertEqual(opsclear.base_label("PR #1 (closed)"), "PR #1")
        self.assertEqual(opsclear.base_label("PR #1 (merged)"), "PR #1")
        self.assertEqual(opsclear.base_label("PR #1"), "PR #1")
        self.assertEqual(opsclear.base_label(None), "PR")
        self.assertEqual(opsclear.base_label("(closed)"), "PR")

    def test_merged_label_replaces_closed(self):
        self.assertEqual(opsclear.merged_label("PR #1 (closed)"), "PR #1 (merged)")
        self.assertEqual(len(opsclear.merged_label("y" * 99 + " (closed)")), 100)


PR_URL = "https://github.com/o/r/pull/7"
PR_BASE = "/api/projects/PRJ-013/jobs"


def pr_args(action, body="", merged=False, branch="feature/JOB-1-x", title="Do it (JOB-1)", extra=()):
    path = os.path.join(tempfile.mkdtemp(), "body.md")
    with open(path, "w", encoding="utf-8") as f:
        f.write(body)
    argv = ["pr", "sync", "--action", action, "--number", "7", "--url", PR_URL, "--branch", branch,
            "--title", title, "--body-file", path, "--yes"]
    if merged:
        argv.append("--merged")
    return argv + list(extra)


def pr_routes(*jobs):
    routes = {
        ("GET", "/api/projects"): (200, [PRJ_NEW]),
        ("GET", "/api/projects/PRJ-013"): (200, PRJ_NEW),
    }
    for j in jobs:
        routes[("GET", f"{PR_BASE}/{j['friendlyId']}")] = (200, j)
        routes[("POST", f"{PR_BASE}/{j['friendlyId']}/links")] = (201, {})
        routes[("PUT", f"{PR_BASE}/{j['friendlyId']}/links/l1")] = (200, {})
        routes[("PATCH", f"{PR_BASE}/{j['friendlyId']}/status")] = (200, {})
    return routes


def link(label):
    return [{"id": "l1", "url": PR_URL, "label": label}]


def write_summary(w):
    return [(c["method"], c["path"].replace(PR_BASE + "/", ""), c["body"]) for c in w]


class PrSyncTest(unittest.TestCase):
    def sync(self, argv, *jobs, routes=None):
        return run(argv, routes or pr_routes(*jobs))

    def test_opened_adds_link_and_starts_new_job(self):
        code, out, _, fake = self.sync(pr_args("opened"), job("JOB-1"))
        self.assertEqual(code, 0)
        self.assertEqual(write_summary(fake.writes()), [
            ("POST", "JOB-1/links", {"url": PR_URL, "label": "PR #7: Do it (JOB-1)"}),
            ("PATCH", "JOB-1/status", {"status": "IN_PROGRESS"}),
        ])
        self.assertIn("JOB-1: add link", out)

    def test_opened_label_cut_to_100(self):
        _, _, _, fake = self.sync(pr_args("opened", title="t" * 200 + " JOB-1"), job("JOB-1"))
        self.assertEqual(len(fake.writes()[0]["body"]["label"]), 100)

    def test_opened_keeps_existing_link_and_other_statuses(self):
        for status in ("IN_PROGRESS", "BLOCKED", "COMPLETED"):
            _, out, _, fake = self.sync(pr_args("opened"), job("JOB-1", status=status, links=link("mine")))
            self.assertEqual(fake.writes(), [], status)
        self.assertIn("already Completed", out)

    def test_opened_completed_job_gets_link_only(self):
        _, _, _, fake = self.sync(pr_args("opened"), job("JOB-1", status="COMPLETED"))
        self.assertEqual([w["method"] for w in fake.writes()], ["POST"])

    def test_edited_adds_only_missing(self):
        body = "Closes JOB-2\n"
        _, _, _, fake = self.sync(pr_args("edited", body=body),
                                  job("JOB-1", status="IN_PROGRESS", links=link("a")), job("JOB-2"))
        self.assertEqual(write_summary(fake.writes()), [
            ("POST", "JOB-2/links", {"url": PR_URL, "label": "PR #7: Do it (JOB-1)"}),
            ("PATCH", "JOB-2/status", {"status": "IN_PROGRESS"}),
        ])

    def test_reopened_removes_closed_suffix(self):
        _, _, _, fake = self.sync(pr_args("reopened"),
                                  job("JOB-1", status="IN_PROGRESS", links=link("PR #7 (closed)")))
        self.assertEqual(write_summary(fake.writes()), [("PUT", "JOB-1/links/l1", {"url": PR_URL, "label": "PR #7"})])

    def test_reopened_adds_missing_link_and_starts(self):
        _, _, _, fake = self.sync(pr_args("reopened"), job("JOB-1"))
        self.assertEqual([w["method"] for w in fake.writes()], ["POST", "PATCH"])

    def test_merged_closes_in_progress_job(self):
        _, _, _, fake = self.sync(pr_args("closed", "Closes JOB-1", merged=True),
                                  job("JOB-1", status="IN_PROGRESS", links=link("PR #7")))
        self.assertEqual(write_summary(fake.writes()), [
            ("PUT", "JOB-1/links/l1", {"url": PR_URL, "label": "PR #7 (merged)"}),
            ("PATCH", "JOB-1/status", {"status": "COMPLETED"}),
        ])

    def test_merged_closes_new_job_in_two_steps(self):
        _, _, _, fake = self.sync(pr_args("closed", "Closes JOB-1", merged=True), job("JOB-1", links=link("PR #7")))
        self.assertEqual([w["body"] for w in fake.writes() if w["method"] == "PATCH"],
                         [{"status": "IN_PROGRESS"}, {"status": "COMPLETED"}])

    def test_merged_unblocks_and_completes_blocked_job(self):
        _, _, _, fake = self.sync(pr_args("closed", "Closes JOB-1", merged=True),
                                  job("JOB-1", status="BLOCKED", links=link("PR #7 (closed)")))
        self.assertEqual(write_summary(fake.writes()), [
            ("PUT", "JOB-1/links/l1", {"url": PR_URL, "label": "PR #7 (merged)"}),
            ("PATCH", "JOB-1/status", {"status": "IN_PROGRESS"}),
            ("PATCH", "JOB-1/status", {"status": "COMPLETED"}),
        ])

    def test_merged_completed_job_is_noop_for_status(self):
        _, _, _, fake = self.sync(pr_args("closed", "Closes JOB-1", merged=True),
                                  job("JOB-1", status="COMPLETED", links=link("PR #7 (merged)")))
        self.assertEqual(fake.writes(), [])

    def test_merged_without_closes_only_starts_new_jobs(self):
        _, _, _, fake = self.sync(pr_args("closed", "Part of JOB-1", merged=True), job("JOB-1", links=link("PR #7")))
        self.assertEqual(write_summary(fake.writes()), [
            ("PUT", "JOB-1/links/l1", {"url": PR_URL, "label": "PR #7 (merged)"}),
            ("PATCH", "JOB-1/status", {"status": "IN_PROGRESS"}),
        ])
        for status in ("IN_PROGRESS", "BLOCKED", "COMPLETED"):
            _, _, _, fake = self.sync(pr_args("closed", merged=True),
                                      job("JOB-1", status=status, links=link("PR #7 (merged)")))
            self.assertEqual(fake.writes(), [], status)

    def test_merged_missing_link_is_added_merged(self):
        _, _, _, fake = self.sync(pr_args("closed", merged=True), job("JOB-1", status="IN_PROGRESS"))
        self.assertEqual(fake.writes()[0]["body"], {"url": PR_URL, "label": "PR #7: Do it (JOB-1) (merged)"})

    def test_closed_without_merge_relabels_only(self):
        _, _, _, fake = self.sync(pr_args("closed", "Closes JOB-1"),
                                  job("JOB-1", status="IN_PROGRESS", links=link("PR #7")))
        self.assertEqual(write_summary(fake.writes()), [
            ("PUT", "JOB-1/links/l1", {"url": PR_URL, "label": "PR #7 (closed)"})])

    def test_closed_without_link_adds_closed_link(self):
        _, _, _, fake = self.sync(pr_args("closed"), job("JOB-1"))
        self.assertEqual(write_summary(fake.writes()), [
            ("POST", "JOB-1/links", {"url": PR_URL, "label": "PR #7: Do it (JOB-1) (closed)"})])

    def test_edit_on_closed_pr_moves_no_status(self):
        argv = pr_args("edited", "Closes JOB-2", extra=["--state", "closed"])
        _, _, _, fake = self.sync(argv, job("JOB-1", links=link("PR #7 (closed)")), job("JOB-2"))
        self.assertEqual(write_summary(fake.writes()), [
            ("POST", "JOB-2/links", {"url": PR_URL, "label": "PR #7: Do it (JOB-1) (closed)"})])

    def test_edit_on_merged_pr_labels_merged_without_status(self):
        argv = pr_args("edited", extra=["--state", "closed", "--merged"])
        _, _, _, fake = self.sync(argv, job("JOB-1", links=link("PR #7 (closed)")))
        self.assertEqual(write_summary(fake.writes()), [
            ("PUT", "JOB-1/links/l1", {"url": PR_URL, "label": "PR #7 (merged)"})])

    def test_edit_on_closed_pr_from_event_file(self):
        path = self.event_file("edited", state="closed", merged=True)
        _, _, _, fake = self.sync(["pr", "sync", "--event", path, "--yes"], job("JOB-1", status="IN_PROGRESS"))
        self.assertEqual([w["method"] for w in fake.writes()], ["POST"])
        self.assertTrue(fake.writes()[0]["body"]["label"].endswith("(merged)"))

    def test_bad_usage_without_key_is_usage_error(self):
        code, _, err, _ = run(["pr", "sync", "--event", "/nonexistent/e.json"], {}, env={"HOME": "/nonexistent"})
        self.assertEqual(code, opsclear.EXIT_USAGE)

    def event_file(self, action, **pr):
        data = {"action": action, "pull_request": {
            "number": 7, "html_url": PR_URL, "title": "T (JOB-1)", "body": None,
            "head": {"ref": "x"}, "merged": False, **pr}}
        path = os.path.join(tempfile.mkdtemp(), "event.json")
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f)
        return path

    def test_unknown_action_is_ignored(self):
        code, out, _, fake = run(["pr", "sync", "--event", self.event_file("synchronize")], {})
        self.assertEqual(code, 0)
        self.assertEqual(fake.calls, [])

    def test_event_file_input_and_summary(self):
        summary = os.path.join(tempfile.mkdtemp(), "summary.md")
        argv = ["pr", "sync", "--event", self.event_file("opened"), "--summary", summary, "--yes"]
        code, _, _, fake = self.sync(argv, job("JOB-1"))
        self.assertEqual(code, 0)
        self.assertEqual(len(fake.writes()), 2)
        with open(summary, encoding="utf-8") as f:
            text = f.read()
        self.assertIn("| Job | Change | Result |", text)
        self.assertIn("| JOB-1 | status -> IN_PROGRESS | done |", text)

    def test_missing_event_file_is_usage_error(self):
        code, _, err, _ = run(["pr", "sync", "--event", "/nonexistent/event.json"], {})
        self.assertEqual(code, opsclear.EXIT_USAGE)
        self.assertIn("cannot read event file", err)

    def test_missing_flags_is_usage_error(self):
        code, _, err, _ = run(["pr", "sync", "--action", "opened"], {})
        self.assertEqual(code, opsclear.EXIT_USAGE)
        self.assertIn("--number", err)

    def test_no_job_referenced(self):
        code, out, _, fake = self.sync(pr_args("opened", branch="dependabot/x", title="Bump y"))
        self.assertEqual(code, 0)
        self.assertIn("references no OpsClear job", out)
        self.assertEqual(fake.calls, [])

    def test_missing_key_exits_zero(self):
        code, out, err, fake = run(pr_args("opened"), {}, env={"HOME": "/nonexistent"})
        self.assertEqual(code, 0)
        self.assertIn("nothing synced", out)
        self.assertEqual(fake.calls, [])

    def test_dry_run_sends_nothing(self):
        code, out, _, fake = self.sync(pr_args("opened", extra=["--dry-run"]), job("JOB-1"))
        self.assertEqual(code, 0)
        self.assertEqual(fake.writes(), [])
        self.assertIn("dry run", out)

    def test_completion_needs_yes(self):
        argv = [a for a in pr_args("closed", "Closes JOB-1", merged=True) if a != "--yes"]
        code, out, _, fake = self.sync(argv, job("JOB-1", status="IN_PROGRESS", links=link("PR #7")))
        self.assertEqual(code, 0)
        self.assertEqual([w["method"] for w in fake.writes()], ["PUT"])
        self.assertIn("re-run with --yes", out)

    def test_completion_requested_for_gated_project(self):
        routes = pr_routes(job("JOB-1", status="IN_PROGRESS", links=link("PR #7")))
        routes[("PATCH", f"{PR_BASE}/JOB-1/status")] = (202, {})
        _, out, _, _ = self.sync(pr_args("closed", "Closes JOB-1", merged=True), routes=routes)
        self.assertIn("completion requested", out)

    def test_missing_job_is_a_warning_and_others_continue(self):
        argv = pr_args("opened", body="Closes JOB-9\nPart of JOB-1")
        code, out, _, fake = self.sync(argv, job("JOB-1"))
        self.assertEqual(code, 0)
        self.assertIn("::warning::JOB-9: skipped", out)
        self.assertEqual([w["path"] for w in fake.writes()][0], f"{PR_BASE}/JOB-1/links")

    def test_failed_step_stops_that_jobs_chain_only(self):
        routes = pr_routes(job("JOB-1", status="BLOCKED", links=link("PR #7")), job("JOB-2", status="IN_PROGRESS"))
        routes[("PATCH", f"{PR_BASE}/JOB-1/status")] = (403, {"message": "forbidden"})
        argv = pr_args("closed", "Closes JOB-1, JOB-2", merged=True)
        code, out, _, fake = self.sync(argv, routes=routes)
        self.assertEqual(code, 0)
        self.assertIn("::warning::JOB-1: skipped", out)
        patches = [c for c in fake.writes() if c["method"] == "PATCH"]
        self.assertEqual([c["path"] for c in patches],
                         [f"{PR_BASE}/JOB-1/status", f"{PR_BASE}/JOB-2/status"])
        self.assertEqual(patches[1]["body"], {"status": "COMPLETED"})

    def test_key_is_not_printed(self):
        _, out, err, _ = self.sync(pr_args("opened"), job("JOB-1"))
        self.assertNotIn(KEY, out + err)


class PrSyncRetryTest(unittest.TestCase):
    def run_with(self, script, argv):
        """script: Responses or exceptions handed out in order; the last one repeats."""
        calls, sleeps = [], []

        def transport(method, url, headers, data):
            calls.append((method, urlsplit(url).path))
            item = script[min(len(calls) - 1, len(script) - 1)]
            if isinstance(item, Exception):
                raise item
            return item

        out, err = io.StringIO(), io.StringIO()
        code = opsclear.main(argv, env={"OPSCLEAR_API_KEY": KEY, "OPSCLEAR_URL": URL}, transport=transport,
                             stdout=out, stderr=err, sleep=sleeps.append, retry_delays=(1, 2, 3))
        return code, out.getvalue(), calls, sleeps

    def test_retries_connection_errors_then_succeeds(self):
        ok = opsclear.Response(200, json.dumps([]).encode())
        down = opsclear.CliError("cannot reach OpsClear")
        code, out, calls, sleeps = self.run_with([down, opsclear.Response(503, b""), ok], pr_args("opened"))
        self.assertEqual(code, 0)
        self.assertEqual(sleeps, [1, 2])
        self.assertIn("retrying in 1s", out)
        self.assertIn("not found in any project", out)  # got through to the (empty) project list

    def test_gives_up_after_delays_and_skips_remaining_jobs(self):
        down = opsclear.CliError("cannot reach OpsClear")
        code, out, calls, sleeps = self.run_with([down], pr_args("opened", body="Closes JOB-2"))
        self.assertEqual(code, 0)
        self.assertEqual(sleeps, [1, 2, 3])
        self.assertEqual(len(calls), 4)
        self.assertIn("::warning::JOB-1: OpsClear unreachable after 4 attempts", out)

    def test_read_phase_errors_are_retried_and_never_crash(self):
        import http.client
        for exc in (TimeoutError("timed out"), ConnectionResetError(), http.client.RemoteDisconnected("closed")):
            code, out, calls, sleeps = self.run_with([exc], pr_args("opened", body="Closes JOB-2"))
            self.assertEqual(code, 0, exc)
            self.assertEqual(sleeps, [1, 2, 3])
            self.assertIn("::warning::JOB-1: OpsClear unreachable", out)
            self.assertIn("::warning::JOB-2: skipped: OpsClear unreachable", out)

    def test_urllib_transport_maps_read_errors(self):
        from unittest import mock
        with mock.patch.object(opsclear.urllib.request, "urlopen", side_effect=TimeoutError("read timed out")):
            with self.assertRaises(opsclear.CliError):
                opsclear.urllib_transport("GET", URL + "/api/projects", {}, None)

    def test_retried_status_change_already_applied_is_done(self):
        j = job("JOB-1", status="NEW", links=link("PR #7"))
        state = {"patches": 0}
        calls = []

        def transport(method, url, headers, data):
            path = urlsplit(url).path
            calls.append((method, path))
            if method == "GET" and path == "/api/projects":
                return opsclear.Response(200, json.dumps([PRJ_NEW]).encode())
            if method == "GET" and path == f"{PR_BASE}/JOB-1":
                # after the first status PATCH the job really is IN_PROGRESS
                cur = dict(j, status="IN_PROGRESS" if state["patches"] else "NEW")
                return opsclear.Response(200, json.dumps(cur).encode())
            if method == "GET":
                return opsclear.Response(200, json.dumps(PRJ_NEW).encode())
            if method == "PATCH":
                state["patches"] += 1
                if state["patches"] == 1:
                    return opsclear.Response(504, b"")
                if state["patches"] >= 3:
                    return opsclear.Response(200, b"{}")
                return opsclear.Response(400, json.dumps({"message": "illegal transition"}).encode())
            return opsclear.Response(200, b"{}")

        out, err = io.StringIO(), io.StringIO()
        argv = pr_args("closed", "Closes JOB-1", merged=True)
        code = opsclear.main(argv, env={"OPSCLEAR_API_KEY": KEY, "OPSCLEAR_URL": URL}, transport=transport,
                             stdout=out, stderr=err, sleep=lambda s: None, retry_delays=(1,))
        self.assertEqual(code, 0)
        self.assertIn("done (applied by an earlier attempt)", out.getvalue())
        self.assertNotIn("::warning::", out.getvalue())
        # the chain went on: IN_PROGRESS (504, then 400 treated as done), then COMPLETED
        self.assertEqual(state["patches"], 3)

    def test_4xx_is_not_retried(self):
        code, out, calls, sleeps = self.run_with([opsclear.Response(403, b"{}")], pr_args("opened"))
        self.assertEqual(code, 0)
        self.assertEqual(sleeps, [])

    def test_default_schedule_is_about_three_minutes(self):
        self.assertEqual(opsclear.RETRY_DELAYS, (10, 20, 40, 60, 60))


class PrCheckRulesTest(unittest.TestCase):
    """ADR-0056 rules: good and bad examples, several from the real PRs listed in the ADR."""

    def title(self, t):
        return opsclear.check_title(t)

    def test_good_titles(self):
        for t in ("feat(backend): add org-level template CRUD (JOB-109)",
                  "test(e2e): approvals flow (JOB-224)",
                  "chore(ci): run checks (JOB-1, JOB-2)",
                  "docs(adr): add ADR-0056 (JOB-295)"):
            self.assertTrue(self.title(t).ok, t)

    def test_bare_scope_titles_fail(self):  # #456, #457, #475, #464
        for t in ("chore: tidy things (JOB-456)", "test: more coverage (JOB-464)"):
            r = self.title(t)
            self.assertFalse(r.ok, t)
            self.assertIn("missing scope", r.problem)

    def test_missing_job_id_fails(self):  # #420, #462
        r = self.title("test(e2e): cypress specs")
        self.assertIn("job ID", r.problem)
        self.assertFalse(self.title("fix(backend): thing (JOB-)").ok)

    def test_unknown_type_scope_and_shape(self):
        self.assertIn("unknown type", self.title("feature(backend): x (JOB-1)").problem)
        self.assertIn("unknown scope", self.title("feat(user): x (JOB-1)").problem)
        self.assertIn("does not match", self.title("Add things (JOB-1)").problem)
        self.assertFalse(self.title("feat(backend):  (JOB-1)").ok)
        self.assertFalse(self.title("feat(backend): x (job-1)").ok)

    def test_branch_rules(self):
        self.assertTrue(opsclear.check_branch("chore/JOB-324-pr-check", ["JOB-324"]).ok)
        self.assertTrue(opsclear.check_branch("e2e/JOB-1-a", ["JOB-2", "JOB-1"]).ok)
        for bad in ("worktree-agent-abb59", "feat/JOB-1-x", "chore/JOB-1-Upper", "chore/JOB-1", "chore/x-JOB-1-y",
                    "revert-12-chore/JOB-1-x"):
            self.assertFalse(opsclear.check_branch(bad, ["JOB-1"]).ok, bad)
        r = opsclear.check_branch("chore/JOB-9-x", ["JOB-1"])
        self.assertIn("not one of the title's", r.problem)

    def test_body_rules(self):
        ok = opsclear.check_body
        self.assertTrue(ok("## Summary\n\nCloses JOB-1\n", ["JOB-1"]).ok)
        self.assertTrue(ok("Part of JOB-1", ["JOB-1"]).ok)
        self.assertTrue(ok("Closes JOB-5, JOB-1", ["JOB-1"]).ok)
        self.assertTrue(ok("Closes JOB-5\nPart of JOB-1", ["JOB-1", "JOB-2"]).ok)
        self.assertIn("no `Closes", ok("Fixes JOB-1", ["JOB-1"]).problem)
        self.assertIn("none of the title's", ok("Closes JOB-5", ["JOB-1"]).problem)
        self.assertIn("placeholder", ok("Closes JOB-1\nReferences JOB-___", ["JOB-1"]).problem)

    def test_body_html_comments_ignored(self):
        self.assertFalse(opsclear.check_body("<!-- Closes JOB-1 -->", ["JOB-1"]).ok)
        self.assertTrue(opsclear.check_body("Closes JOB-1\n<!-- JOB-___ Co-Authored-By: Claude -->", ["JOB-1"]).ok)

    def test_ai_attribution(self):
        for line in ("Co-Authored-By: Claude <noreply@anthropic.com>", "co-authored-by: Copilot <x@y>",
                     "Co-authored-by: ChatGPT", "Co-Authored-By: Cursor Agent", "Co-Authored-By: Gemini",
                     "Co-Authored-By: OpenAI Codex", "Co-Authored-By: someone <a@anthropic.com>",
                     "Generated with Claude Code", "\U0001f916 Generated with [Claude Code](https://claude.com)"):
            r = opsclear.check_body(f"Closes JOB-1\n\n{line}\n", ["JOB-1"])
            self.assertIn("AI attribution", r.problem, line)
        human = opsclear.check_body("Closes JOB-1\n\nCo-Authored-By: Jane Doe <jane@example.com>", ["JOB-1"])
        self.assertTrue(human.ok)

    def test_layer_classification(self):
        cases = {
            "backend/src/main/resources/db/migration/V1__x.sql": "db",
            "backend/src/main/generated/jooq/Tables.java": "db",
            "backend/src/main/java/A.java": "backend",
            "backend/src/test/java/ATest.java": "backend",
            "backend/build.gradle": "backend",
            "frontend/src/App.tsx": "frontend",
            "frontend/package.json": "frontend",
            "frontend/cypress/e2e/a.cy.ts": "neutral",
            "docs/dev/x.md": "neutral", ".github/workflows/ci.yml": "neutral", ".claude/settings.json": "neutral",
            "scripts/opsclear": "neutral", "api/postman/c.json": "neutral", "keycloak/realm.json": "neutral",
            "README.md": "neutral",
        }
        for path, layer in cases.items():
            self.assertEqual(opsclear.layer_of(path), layer, path)

    def test_layer_check(self):
        layers = opsclear.check_layers
        self.assertTrue(layers(["docs/a.md", "frontend/cypress/a.ts"], []).ok)
        self.assertTrue(layers(["frontend/src/a.ts", "frontend/cypress/a.ts", "docs/a.md"], []).ok)
        self.assertTrue(layers(["backend/src/main/resources/db/migration/V1__x.sql",
                                "backend/src/main/generated/A.java"], []).ok)  # #483: one layer
        mixed = ["backend/src/main/resources/db/migration/V1__x.sql", "backend/src/test/java/ATest.java"]  # #431
        r = layers(mixed, [])
        self.assertIn("db, backend", r.problem)
        self.assertTrue(layers(mixed, ["Cross-Layer"]).ok)
        self.assertIn("db, backend", layers(mixed, ["cross-layer"]).detail)
        self.assertFalse(layers(["backend/a", "frontend/b"], ["other"]).ok)
        self.assertTrue(layers(["backend/a", "frontend/b", "backend/src/main/generated/c"], ["cross-layer"]).ok)

    def test_too_many_files_fails(self):
        r = opsclear.check_layers([f"docs/{i}.md" for i in range(3000)], ["cross-layer"])
        self.assertIn("Split the PR", r.fix)
        self.assertTrue(opsclear.check_layers([f"docs/{i}.md" for i in range(2999)], []).ok)

    def test_all_failures_reported(self):
        results = opsclear.run_checks("chore: x", "nothing", "main", [], ["backend/a", "frontend/b"])
        self.assertEqual([r.ok for r in results], [False] * 4)

    def test_real_prs(self):
        # #496: backend fix with a Cypress regression test is one layer
        results = opsclear.run_checks("fix(backend): keep SSE pushes going (JOB-329)", "Closes JOB-329",
                                      "fix/JOB-329-sse", [],
                                      ["backend/src/main/java/A.java", "frontend/cypress/e2e/a.cy.ts"])
        self.assertTrue(all(r.ok for r in results))
        # #420, #425, #426: e2e scope was unlisted, now fine
        self.assertTrue(opsclear.check_title("test(e2e): approvals (JOB-420)").ok)


class PrCheckCliTest(unittest.TestCase):
    GOOD = {"title": "chore(ci): add the check (JOB-324)", "body": "Closes JOB-324\n", "branch": "chore/JOB-324-x"}

    def local(self, extra=(), title=None, body=None, branch=None, git=None):
        d = tempfile.mkdtemp()
        path = os.path.join(d, "body.md")
        with open(path, "w", encoding="utf-8") as f:
            f.write(self.GOOD["body"] if body is None else body)
        argv = ["pr", "check", "--title", title or self.GOOD["title"], "--body-file", path] + list(extra)
        if branch is not False:
            argv += ["--branch", branch or self.GOOD["branch"]]
        git = git or (lambda args: "scripts/opsclear\ndocs/dev/process/NAMING.md\n")
        original = opsclear.run_git
        opsclear.run_git = git
        try:
            return run(argv, {}, env={"HOME": "/nonexistent"})
        finally:
            opsclear.run_git = original

    def test_local_pass_needs_no_key(self):
        code, out, err, fake = self.local()
        self.assertEqual(code, 0)
        self.assertEqual(err, "")
        self.assertIn("PASS  Layers", out)
        self.assertEqual(fake.calls, [])

    def test_local_failure_exit_1_with_annotations(self):
        code, out, err, _ = self.local(title="chore: x")
        self.assertEqual(code, 1)
        self.assertIn("::error title=Process check%3A PR title::", out)
        self.assertIn("missing scope", out)
        self.assertIn("PASS  PR body", out)

    def test_local_git_fallbacks(self):
        calls = []

        def git(args):
            calls.append(args)
            return "feature/JOB-324-x\n" if args[0] == "rev-parse" else "frontend/a.ts\nbackend/b.java\n"

        code, out, _, _ = self.local(branch=False, git=git, title="feat(frontend): x (JOB-324)")
        self.assertEqual(code, 1)
        self.assertEqual(calls, [["rev-parse", "--abbrev-ref", "HEAD"], ["diff", "--name-only", "origin/main...HEAD"]])
        self.assertIn("PASS  Branch name: feature/JOB-324-x", out)
        self.assertIn("touches 2 layers", out)
        code, _, _, _ = self.local(extra=["--base", "upstream/dev", "--labels", "a, cross-layer"],
                                   branch=False, git=git, title="feat(frontend): x (JOB-324)")
        self.assertEqual(code, 0)
        self.assertEqual(calls[-1], ["diff", "--name-only", "upstream/dev...HEAD"])

    def test_git_failure_is_an_error(self):
        def git(args):
            raise opsclear.CliError("git diff failed: bad revision")
        code, _, err, _ = self.local(git=git)
        self.assertEqual(code, 1)
        self.assertIn("bad revision", err)

    def test_needs_event_or_title(self):
        code, _, err, _ = run(["pr", "check"], {}, env={})
        self.assertEqual(code, opsclear.EXIT_USAGE)
        self.assertIn("--title", err)

    def test_summary_table(self):
        summary = os.path.join(tempfile.mkdtemp(), "s.md")
        self.local(extra=["--summary", summary], title="chore: x")
        with open(summary, encoding="utf-8") as f:
            text = f.read()
        self.assertIn("| Check | Result | Detail |", text)
        self.assertIn("| PR title | FAIL |", text)
        self.assertIn("| Layers | pass | neutral paths only |", text)

    def test_annotation_escaping(self):
        self.assertEqual(opsclear.gh_escape("a\nb%"), "a%0Ab%25")


class PrCheckCiTest(unittest.TestCase):
    ENV = {"GITHUB_TOKEN": "ghs_token", "GITHUB_REPOSITORY": "o/r"}

    def event(self, labels=(), **pr):
        data = {"action": "opened", "pull_request": {
            "number": 7, "title": "chore(ci): add (JOB-324)", "body": "Closes JOB-324",
            "head": {"ref": "chore/JOB-324-x"}, "labels": [{"name": n} for n in labels], **pr}}
        path = os.path.join(tempfile.mkdtemp(), "event.json")
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f)
        return path

    def run_with(self, script, argv, env=None):
        calls, sleeps = [], []

        def transport(method, url, headers, data):
            calls.append((method, url, headers))
            item = script[min(len(calls) - 1, len(script) - 1)]
            if isinstance(item, Exception):
                raise item
            return item

        out, err = io.StringIO(), io.StringIO()
        code = opsclear.main(argv, env=self.ENV if env is None else env, transport=transport, stdout=out,
                             stderr=err, sleep=sleeps.append)
        return code, out.getvalue(), err.getvalue(), calls, sleeps

    @staticmethod
    def files(*names):
        return opsclear.Response(200, json.dumps([{"filename": n} for n in names]).encode())

    def test_ci_mode_pass(self):
        code, out, _, calls, _ = self.run_with([self.files("scripts/opsclear")],
                                               ["pr", "check", "--event", self.event()])
        self.assertEqual(code, 0)
        method, url, headers = calls[0]
        self.assertEqual(url, "https://api.github.com/repos/o/r/pulls/7/files?per_page=100&page=1")
        self.assertEqual(headers["Authorization"], "Bearer ghs_token")

    def test_ci_mode_label_from_event(self):
        names = ["backend/a.java", "frontend/b.ts"]
        code, _, _, _, _ = self.run_with([self.files(*names)], ["pr", "check", "--event", self.event()])
        self.assertEqual(code, 1)
        code, _, _, _, _ = self.run_with([self.files(*names)],
                                         ["pr", "check", "--event", self.event(["cross-layer"])])
        self.assertEqual(code, 0)

    def test_pagination(self):
        page1 = self.files(*[f"docs/{i}.md" for i in range(100)])
        page2 = opsclear.Response(200, json.dumps(
            [{"filename": "backend/a.java", "previous_filename": "frontend/x.ts"}]).encode())
        code, out, _, calls, _ = self.run_with([page1, page2], ["pr", "check", "--event", self.event()])
        self.assertEqual(len(calls), 2)
        self.assertIn("page=2", calls[1][1])
        self.assertEqual(code, 1)  # the rename's old path counts: backend + frontend
        self.assertIn("touches 2 layers: backend, frontend", out)

    def test_retry_then_succeed(self):
        script = [opsclear.CliError("boom"), opsclear.Response(502, b"bad gateway"), self.files("docs/a.md")]
        code, _, _, calls, sleeps = self.run_with(script, ["pr", "check", "--event", self.event()])
        self.assertEqual(code, 0)
        self.assertEqual(sleeps, [2, 4])
        self.assertEqual(len(calls), 3)

    def test_retry_then_fail(self):
        code, out, err, calls, sleeps = self.run_with([opsclear.Response(500, b"{}")],
                                                      ["pr", "check", "--event", self.event()])
        self.assertEqual(code, 1)
        self.assertEqual(len(calls), len(opsclear.CHECK_RETRY_DELAYS) + 1)
        self.assertIn("cannot list the PR's files", err)
        self.assertNotIn("PASS", out)  # a required check must not pass on missing data

    def test_client_error_is_not_retried(self):
        code, _, err, calls, sleeps = self.run_with([opsclear.Response(404, b'{"message":"Not Found"}')],
                                                    ["pr", "check", "--event", self.event()])
        self.assertEqual(code, 1)
        self.assertEqual((len(calls), sleeps), (1, []))
        self.assertIn("Not Found", err)

    def test_missing_token_or_event(self):
        code, _, err, _, _ = self.run_with([], ["pr", "check", "--event", self.event()], env={})
        self.assertEqual(code, 1)
        self.assertIn("GITHUB_TOKEN", err)
        code, _, err, _, _ = self.run_with([], ["pr", "check", "--event", "/nonexistent.json"])
        self.assertEqual(code, opsclear.EXIT_USAGE)


class NamingDocSyncTest(unittest.TestCase):
    """docs/dev/process/NAMING.md and the constants in scripts/opsclear must agree."""

    @classmethod
    def setUpClass(cls):
        path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "docs", "dev", "process", "NAMING.md")
        with open(path, encoding="utf-8") as f:
            cls.text = f.read()

    def ticked(self, label):
        lines = [line for line in self.text.splitlines() if line.startswith(label)]
        self.assertEqual(len(lines), 1, f"NAMING.md needs exactly one line starting {label!r}")
        return re.findall(r"`([^`]+)`", lines[0])

    def test_types(self):
        self.assertEqual(self.ticked("**Types:**"), list(opsclear.TYPES))

    def test_scopes(self):
        self.assertEqual(self.ticked("**Scopes (fixed list):**"), list(opsclear.SCOPES))

    def test_branch_prefixes(self):
        prefixes = re.findall(r"^\|[^|]*\| `(\w+)/<job-id>-<slug>` \|", self.text, re.MULTILINE)
        self.assertEqual(prefixes, list(opsclear.BRANCH_PREFIXES))

    def test_label_and_layer_paths(self):
        self.assertIn(f"`{opsclear.CROSS_LAYER_LABEL}`", self.text)
        for _, prefixes in opsclear.LAYER_PATHS:
            for prefix in prefixes:
                if prefix != "backend/" and prefix != "frontend/":
                    self.assertIn(f"`{prefix}**`", self.text)
        for prefix in opsclear.NEUTRAL_PATHS:
            self.assertIn(f"`{prefix}**`", self.text)
        for tool in opsclear.AI_TOOLS:
            self.assertIn(tool, self.text)


if __name__ == "__main__":
    unittest.main(verbosity=1)
