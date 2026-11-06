#!/usr/bin/env python3
"""Unit tests for scripts/opsclear. No network: HTTP goes through a fake transport.

Run: python3 scripts/opsclear_test.py
"""
import io
import json
import os
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

    def test_closed_without_link_does_nothing(self):
        _, _, _, fake = self.sync(pr_args("closed"), job("JOB-1"))
        self.assertEqual(fake.writes(), [])

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

    def test_4xx_is_not_retried(self):
        code, out, calls, sleeps = self.run_with([opsclear.Response(403, b"{}")], pr_args("opened"))
        self.assertEqual(code, 0)
        self.assertEqual(sleeps, [])

    def test_default_schedule_is_about_three_minutes(self):
        self.assertEqual(opsclear.RETRY_DELAYS, (10, 20, 40, 60, 60))


if __name__ == "__main__":
    unittest.main(verbosity=1)
