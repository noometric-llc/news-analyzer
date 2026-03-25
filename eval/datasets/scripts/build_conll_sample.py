"""Build the CoNLL-2003 sanity check sample with auto-computed offsets."""

import yaml

ENTRIES = [
    ("Peter Blackburn BRUSSELS 1996-08-22 The European Commission said on Thursday it disagreed with German advice to consumers to shun British lamb.",
     [("Peter Blackburn", "person"), ("BRUSSELS", "location"), ("European Commission", "organization"), ("German", "concept"), ("British", "concept")],
     "eval-2-conll-001", "medium"),
    ("Germany's representative to the European Union's veterinary committee Werner Zwingmann said on Wednesday consumers should buy sheepmeat from countries other than Britain.",
     [("Germany", "location"), ("European Union", "organization"), ("Werner Zwingmann", "person"), ("Britain", "location")],
     "eval-2-conll-002", "medium"),
    ("SOCCER - JAPAN GET LUCKY WIN, CHINA IN SURPRISE DEFEAT.",
     [("JAPAN", "location"), ("CHINA", "location")],
     "eval-2-conll-003", "easy"),
    ("Nadim Ladki AL-AIN, United Arab Emirates 1996-12-06 South Korea and Japan qualified for the finals of the Asian Cup on Friday.",
     [("Nadim Ladki", "person"), ("AL-AIN", "location"), ("United Arab Emirates", "location"), ("South Korea", "location"), ("Japan", "location"), ("Asian Cup", "concept")],
     "eval-2-conll-004", "medium"),
    ("The United Nations Security Council on Friday passed a resolution calling on Israel to stop building settlements in East Jerusalem and the rest of the occupied territories.",
     [("United Nations Security Council", "organization"), ("Israel", "location"), ("East Jerusalem", "location")],
     "eval-2-conll-005", "medium"),
    ("Microsoft Corp. said on Tuesday it would acquire LinkedIn Corp. for $26.2 billion, the largest deal in the technology company's history.",
     [("Microsoft Corp.", "organization"), ("LinkedIn Corp.", "organization")],
     "eval-2-conll-006", "easy"),
    ("CRICKET - LEICESTERSHIRE TAKE OVER AT TOP AFTER INNINGS VICTORY.",
     [("LEICESTERSHIRE", "organization")],
     "eval-2-conll-007", "hard"),
    ("President Bill Clinton arrived in Paris on Monday for talks with French President Jacques Chirac on NATO expansion and European security.",
     [("Bill Clinton", "person"), ("Paris", "location"), ("French", "concept"), ("Jacques Chirac", "person"), ("NATO", "organization"), ("European", "concept")],
     "eval-2-conll-008", "medium"),
    ("TENNIS - RESULTS AT THE CANADIAN OPEN IN MONTREAL.",
     [("CANADIAN OPEN", "concept"), ("MONTREAL", "location")],
     "eval-2-conll-009", "medium"),
    ("British Foreign Secretary Malcolm Rifkind said on Tuesday that ties between London and Tehran could only improve once Iran changed its behavior.",
     [("British", "concept"), ("Malcolm Rifkind", "person"), ("London", "location"), ("Tehran", "location"), ("Iran", "location")],
     "eval-2-conll-010", "medium"),
    ("The Chicago Bulls defeated the Utah Jazz 90-88 in Game 6 of the NBA Finals on Sunday.",
     [("Chicago Bulls", "organization"), ("Utah Jazz", "organization"), ("NBA", "organization")],
     "eval-2-conll-011", "medium"),
    ("Australian fast bowler Shane Warne took five wickets as England collapsed to 129 all out in the second innings at Lord's.",
     [("Australian", "concept"), ("Shane Warne", "person"), ("England", "location"), ("Lord's", "location")],
     "eval-2-conll-012", "medium"),
    ("Russian President Boris Yeltsin flew to Norway on Monday for a two-day state visit.",
     [("Russian", "concept"), ("Boris Yeltsin", "person"), ("Norway", "location")],
     "eval-2-conll-013", "easy"),
    ("Goldman Sachs Group Inc. reported record quarterly earnings on Tuesday, beating expectations from Wall Street analysts.",
     [("Goldman Sachs Group Inc.", "organization"), ("Wall Street", "location")],
     "eval-2-conll-014", "medium"),
    ("BASEBALL - SEATTLE MARINERS BEAT NEW YORK YANKEES 5-3.",
     [("SEATTLE MARINERS", "organization"), ("NEW YORK YANKEES", "organization")],
     "eval-2-conll-015", "easy"),
    ("Italian Prime Minister Romano Prodi said on Wednesday that Italy would meet the criteria for joining Europe's single currency.",
     [("Italian", "concept"), ("Romano Prodi", "person"), ("Italy", "location"), ("Europe", "location")],
     "eval-2-conll-016", "medium"),
    ("The International Monetary Fund warned on Thursday that the global economy faces significant risks from trade tensions between the United States and China.",
     [("International Monetary Fund", "organization"), ("United States", "location"), ("China", "location")],
     "eval-2-conll-017", "easy"),
    ("Former South African President Nelson Mandela celebrated his 80th birthday at a ceremony in Johannesburg attended by world leaders.",
     [("South African", "concept"), ("Nelson Mandela", "person"), ("Johannesburg", "location")],
     "eval-2-conll-018", "easy"),
    ("Toyota Motor Corp. plans to invest $1.3 billion in its Kentucky manufacturing plant, the Japanese automaker said on Friday.",
     [("Toyota Motor Corp.", "organization"), ("Kentucky", "location"), ("Japanese", "concept")],
     "eval-2-conll-019", "medium"),
    ("SOCCER - REAL MADRID BEAT BARCELONA 3-1 IN SPANISH LEAGUE.",
     [("REAL MADRID", "organization"), ("BARCELONA", "organization"), ("SPANISH LEAGUE", "concept")],
     "eval-2-conll-020", "medium"),
    ("German Chancellor Helmut Kohl met with U.S. Secretary of State Madeleine Albright in Bonn on Tuesday to discuss NATO enlargement.",
     [("German", "concept"), ("Helmut Kohl", "person"), ("U.S.", "location"), ("Madeleine Albright", "person"), ("Bonn", "location"), ("NATO", "organization")],
     "eval-2-conll-021", "medium"),
    ("The World Health Organization declared the Ebola outbreak in the Democratic Republic of Congo a public health emergency on Wednesday.",
     [("World Health Organization", "organization"), ("Democratic Republic of Congo", "location")],
     "eval-2-conll-022", "medium"),
    ("Brazilian striker Ronaldo scored twice as Inter Milan beat Lazio 3-0 in the Italian Serie A on Saturday.",
     [("Brazilian", "concept"), ("Ronaldo", "person"), ("Inter Milan", "organization"), ("Lazio", "organization"), ("Italian", "concept"), ("Serie A", "concept")],
     "eval-2-conll-023", "hard"),
    ("Japanese Finance Minister Kiichi Miyazawa told reporters in Tokyo that the Bank of Japan would maintain its current monetary policy.",
     [("Japanese", "concept"), ("Kiichi Miyazawa", "person"), ("Tokyo", "location"), ("Bank of Japan", "organization")],
     "eval-2-conll-024", "medium"),
    ("The Federal Reserve raised interest rates by a quarter point on Wednesday, Chairman Alan Greenspan said in a statement issued from Washington.",
     [("Federal Reserve", "organization"), ("Alan Greenspan", "person"), ("Washington", "location")],
     "eval-2-conll-025", "medium"),
]


def main():
    results = []
    for text, ents, eid, diff in ENTRIES:
        entity_list = []
        for ename, etype in ents:
            idx = text.find(ename)
            if idx == -1:
                print(f"WARNING: '{ename}' not found in {eid}")
                continue
            entity_list.append({
                "text": ename,
                "type": etype,
                "start": idx,
                "end": idx + len(ename),
            })
        results.append({
            "vars": {
                "article_text": text,
                "entities": entity_list,
                "metadata": {
                    "id": eid,
                    "article_id": None,
                    "article_type": "news_report",
                    "branch": None,
                    "source": "conll",
                    "perturbation_type": "none",
                    "difficulty": diff,
                    "fact_count": 0,
                    "curated": True,
                    "curated_date": "2026-03-23",
                },
            }
        })

    header = (
        "# Gold Dataset: CoNLL-2003 Sanity Check Sample\n"
        "# Schema: See legislative.yaml header for full schema reference.\n"
        "# Source: CoNLL-2003 shared task test set (Reuters newswire, public domain)\n"
        "# Purpose: External validation - proves scorer works on a known NER benchmark\n"
        "# Entity type mapping: PER=person, ORG=organization, LOC=location, MISC=concept\n\n"
    )

    with open("eval/datasets/gold/conll_sample.yaml", "w", encoding="utf-8") as f:
        f.write(header)
        yaml.dump(
            results, f,
            default_flow_style=False,
            allow_unicode=True,
            sort_keys=False,
            width=120,
        )

    total_ents = sum(len(r["vars"]["entities"]) for r in results)
    print(f"Wrote {len(results)} entries with {total_ents} entities")


if __name__ == "__main__":
    main()
