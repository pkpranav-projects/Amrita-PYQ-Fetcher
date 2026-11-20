"""Converts cmd/internal/menu/cys_catalog.go into app/src/main/assets/catalog_seed.json.
Only verified bitstream paths from the Go catalog are emitted - nothing is synthesised."""
import json, re, sys, pathlib, urllib.parse

root = pathlib.Path(__file__).resolve().parents[2]
src = (root / "cmd/internal/menu/cys_catalog.go").read_text(encoding="utf-8")

subj_re = re.compile(r'\{\s*Code:\s*"([^"]+)",\s*Name:\s*"([^"]+)",\s*Semester:\s*(\d+),\s*Papers:\s*\[\]PaperItem\{(.*?)\n\t\t\},\n\t\}', re.S)
paper_re = re.compile(r'Name:\s*"([^"]*)",\s*Path:\s*"([^"]*)",\s*Year:\s*"([^"]*)",\s*Type:\s*"([^"]*)",\s*Exam:\s*"([^"]*)"', re.S)
code_re = re.compile(r'\((\d{2}[A-Z]{2,4}\d{2,3}[A-Z]?)')
handle_re = re.compile(r'/handle/123456789/(\d+)/')

subjects = []
total = 0
for m in subj_re.finditer(src):
    code, name, sem, body = m.groups()
    papers = []
    legacy = []
    seen = set()
    for p in paper_re.finditer(body):
        title, path, year, typ, exam = p.groups()
        if path in seen:
            continue
        seen.add(path)
        decoded = urllib.parse.unquote(path.rsplit("/", 1)[-1])
        cm = code_re.search(title)
        src_code = cm.group(1) if cm else code
        if src_code != code and src_code not in legacy:
            legacy.append(src_code)
        hm = handle_re.search(path)
        papers.append({
            "title": re.sub(r"^\[[^\]]+\]\s*", "", title),
            "path": path,
            "fileName": decoded,
            "handle": hm.group(1) if hm else "",
            "year": int(year),
            "type": typ,
            "exam": exam,
            "sourceCode": src_code,
        })
    total += len(papers)
    subjects.append({"code": code, "name": name, "semester": int(sem), "legacyCodes": legacy, "papers": papers})

branches = [
    {"code": "CYS", "name": "Cyber Security", "description": "B.Tech Computer Science and Engineering (Cyber Security) - BTC-CYS 2024 Curriculum", "featured": True},
    {"code": "CSE", "name": "Computer Science & Engineering", "description": "B.Tech CSE - live DSpace sync", "featured": False},
    {"code": "ECE", "name": "Electronics & Communication", "description": "B.Tech ECE - live DSpace sync", "featured": False},
    {"code": "AIE", "name": "Artificial Intelligence", "description": "B.Tech AI - live DSpace sync", "featured": False},
    {"code": "EEE", "name": "Electrical & Electronics", "description": "B.Tech EEE - live DSpace sync", "featured": False},
    {"code": "MECH", "name": "Mechanical Engineering", "description": "B.Tech Mechanical - live DSpace sync", "featured": False},
    {"code": "MCA", "name": "Master of Computer Applications", "description": "MCA - live DSpace sync", "featured": False},
    {"code": "MTECH", "name": "M.Tech Computer Science", "description": "M.Tech CSE - live DSpace sync", "featured": False},
]
for s in subjects:
    s["branch"] = "CYS"

out = {"version": 1, "branches": branches, "subjects": subjects}
dest = root / "android/app/src/main/assets/catalog_seed.json"
dest.write_text(json.dumps(out, indent=1, ensure_ascii=False), encoding="utf-8")
print(f"subjects={len(subjects)} papers={total}", file=sys.stderr)
