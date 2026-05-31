#!/usr/bin/env python3
"""
Extract the starting page number for each problem heading in the rendered PDF.

The build pipeline:
  1. build_revision_sheet.py emits LLD_Revision_Sheet.docx (TOC field unpopulated)
  2. LibreOffice converts that to LLD_Revision_Sheet.pdf (renders all fields)
  3. THIS SCRIPT scans the PDF for headings like '1. Parking Lot' and emits
     a Python dict mapping heading -> first-page-number.
  4. build_revision_sheet.py is re-run with `STATIC_INDEX_PAGES` baked in.
"""
import re
import sys
import json
from pathlib import Path

from pdfminer.high_level import extract_pages
from pdfminer.layout import LTTextContainer

PDF = Path("/tmp/LLD_Revision_Sheet.pdf")

# Same study sequence as in build_revision_sheet.py
HEADINGS = [
    "1. Parking Lot",
    "2. Snake & Ladder",
    "3. Vending Machine",
    "4. LRU Cache",
    "5. Connect Four",
    "6. Splitwise",
    "7. Movie Ticket Booking",
    "8. Amazon Locker",
    "9. Logging Service",
    "10. Rate Limiter",
    "11. Inventory Management",
    "12. Notification System",
    "13. Job Scheduler",
    "14. Meeting Scheduler",
    "15. Payment Gateway",
    "16. URL Shortener",
    "17. File System",
    "18. Cab Booking",
    "19. Chess",
    "20. Elevator System",
    "Pattern → Problem Index",
    "Concurrency Idiom → Problem Index",
    "Company → Priority Problems",
    "45-Minute Round Playbook",
    "Anti-Pattern Catalog",  # may have trailing parenthetical
]

# The PDF text may have unicode characters that don't match plain ASCII;
# normalize for comparison.
def normalize(s):
    return re.sub(r"\s+", " ", s.strip()).lower()


def find_first_page_for_heading(pages_text, target, skip_pages=2):
    """Return 1-based page number where target heading first appears,
    skipping the first `skip_pages` pages (title + contents)."""
    norm_t = normalize(target)
    for page_num, text in pages_text.items():
        if page_num <= skip_pages:
            continue
        if norm_t in normalize(text):
            return page_num
    return None


def main():
    if not PDF.exists():
        print(f"ERROR: {PDF} not found. Build the PDF first.", file=sys.stderr)
        sys.exit(1)

    pages_text = {}
    for page_num, layout in enumerate(extract_pages(str(PDF)), start=1):
        text = "\n".join(
            el.get_text() for el in layout if isinstance(el, LTTextContainer)
        )
        pages_text[page_num] = text

    print(f"PDF has {len(pages_text)} pages.\n", file=sys.stderr)

    result = {}
    for h in HEADINGS:
        pg = find_first_page_for_heading(pages_text, h)
        if pg is None:
            print(f"  MISS: '{h}' — not found", file=sys.stderr)
        else:
            result[h] = pg
            print(f"  {pg:4d}  {h}", file=sys.stderr)

    # emit as a Python dict literal for easy paste into build script
    print()
    print(json.dumps(result, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
