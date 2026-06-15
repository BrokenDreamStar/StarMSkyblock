#!/usr/bin/env python3
"""
Reorder block-values.yml according to Minecraft Wiki block list order
and update Chinese names to use wiki official translations.

Usage: python3 tools/reorder_blocks.py
"""

import re
import os
import sys

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BLOCK_LIST_PATH = os.path.join(PROJECT_ROOT, 'src/main/resources/block_list.yml')
BLOCK_VALUES_PATH = os.path.join(PROJECT_ROOT, 'src/main/resources/block-values.yml')
OUTPUT_PATH = BLOCK_VALUES_PATH  # Overwrite in place

# ─── Parse block_list.yml ────────────────────────────────────────────────────

def parse_wiki_entries(filepath):
    """Parse block_list.yml and return list of (index, filename, chinese_name) tuples."""
    entries = []
    with open(filepath, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    for idx, line in enumerate(lines, start=1):
        raw = line.rstrip('\n')

        # Skip non-entry lines
        if not raw.startswith('*') and not raw.startswith('{{') and not raw.startswith('|'):
            continue

        # Handle {{only|be|for=...}} wrapped entries
        only_match = re.match(r'\{\{only\|be\|for=(.+)\}\}', raw)
        if only_match:
            inner = only_match.group(1)
        else:
            inner = raw.lstrip('* ')
            # Remove trailing {{only|be}} markers
            inner = re.sub(r'\{\{only\|[^}]*\}\}\s*$', '', inner).strip()
            # Remove trailing }} from columns-list
            inner = re.sub(r'\}\}\s*$', '', inner).strip()

        # Skip {{BlockSprite|...}} entries (no file reference)
        if inner.startswith('{{BlockSprite') or inner.startswith('{{Sprite'):
            continue

        # Skip columns-list wrapper lines
        if inner.startswith('{{columns-list'):
            continue

        # Extract filename from [[File:...]] or [[FIle:...]]
        file_match = re.search(r'\[\[[Ff][Ii][Ll][Ee]:([^\]]+)\]\]', inner)
        if not file_match:
            continue

        raw_filename = file_match.group(1)
        # Handle image size suffix: "Filename.png|30px" -> "Filename.png"
        filename = raw_filename.split('|')[0].strip()

        # Extract Chinese name
        rest = inner[file_match.end():].strip()

        # Remove bold markers
        rest = rest.replace("'''", "")

        # Extract link text from [[...]]
        link_match = re.search(r'\[\[([^\]]+)\]\]', rest)
        if not link_match:
            continue

        link_content = link_match.group(1)

        # Handle [[Display|{{tr|CN|TW|HK}}]] — take display text before pipe
        if '|' in link_content:
            chinese_name = link_content.split('|')[0]
        else:
            chinese_name = link_content

        # Strip trailing engineering names like （作为方块）
        chinese_name = re.sub(r'[（(][^）)]*[）)]', '', chinese_name).strip()

        entries.append((idx, filename, chinese_name))

    return entries


# ─── Parse block-values.yml ──────────────────────────────────────────────────

def parse_block_values(filepath):
    """Parse block-values.yml and return sections."""
    with open(filepath, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    # Split into header / blocks / tail sections
    header_lines = []
    blocks_lines = []
    tail_lines = []
    current = 'header'

    for line in lines:
        if current == 'header':
            header_lines.append(line)
            if line.strip() == 'blocks:':
                current = 'blocks'
        elif current == 'blocks':
            if line.strip().startswith('limits:'):
                current = 'tail'
                tail_lines.append(line)
            else:
                blocks_lines.append(line)
        else:
            tail_lines.append(line)

    # Parse blocks_lines into structured entries + raw lines
    parsed_blocks = []  # list of ('meta', raw_line) or ('material', dict)
    for line in blocks_lines:
        stripped = line.strip()
        if not stripped or stripped.startswith('#'):
            parsed_blocks.append(('meta', line))
        elif ':' in stripped and not stripped.startswith('#'):
            match = re.match(r'^  ([A-Z_]+):\s*(\d+)\s*(?:#\s*(.*))?$', line)
            if match:
                parsed_blocks.append(('material', {
                    'material': match.group(1),
                    'value': match.group(2),
                    'comment': (match.group(3) or '').strip(),
                    'raw': line,
                }))
            else:
                parsed_blocks.append(('meta', line))
        else:
            parsed_blocks.append(('meta', line))

    return header_lines, parsed_blocks, tail_lines


# ─── Matching Logic ──────────────────────────────────────────────────────────

def build_material_set(parsed_blocks):
    """Extract set of all known material names."""
    return {e['material'] for t, e in parsed_blocks if t == 'material'}


def build_material_map(parsed_blocks):
    """Build dict: material -> entry dict."""
    return {e['material']: e for t, e in parsed_blocks if t == 'material'}


# Special cases for conversion
KNOWN_SPECIAL_MATERIALS = {
    # These are known materials whose values may differ but are valid
}

# Set of duplicate filenames that need Chinese name disambiguation
DUP_FILENAME_BASES = {
    'Chiseled Stone Bricks',
    'Cobblestone',
    'Cracked Stone Bricks',
    'Deepslate',
    'Mossy Stone Bricks',
    'Stone',
    'Stone Bricks',
    'Stem',
    'Oak Slab',
    # Copper + exposed + weathered + oxidized variants
    'Copper Block', 'Copper Bars', 'Copper Bulb', 'Copper Chain',
    'Copper Chest', 'Copper Door', 'Copper Golem Statue',
    'Copper Grate', 'Copper Lantern', 'Copper Trapdoor',
    'Cut Copper', 'Cut Copper Slab', 'Cut Copper Stairs',
    'Chiseled Copper', 'Lightning Rod',
    'Exposed Chiseled Copper', 'Exposed Copper Bars',
    'Exposed Copper Block', 'Exposed Copper Bulb',
    'Exposed Copper Chain', 'Exposed Copper Chest',
    'Exposed Copper Door', 'Exposed Copper Golem Statue',
    'Exposed Copper Grate', 'Exposed Copper Lantern',
    'Exposed Copper Trapdoor',
    'Exposed Cut Copper', 'Exposed Cut Copper Slab', 'Exposed Cut Copper Stairs',
    'Exposed Lightning Rod',
    'Oxidized Chiseled Copper', 'Oxidized Copper Bars',
    'Oxidized Copper Block', 'Oxidized Copper Bulb',
    'Oxidized Copper Chain', 'Oxidized Copper Chest',
    'Oxidized Copper Door', 'Oxidized Copper Golem Statue',
    'Oxidized Copper Grate', 'Oxidized Copper Lantern',
    'Oxidized Copper Trapdoor',
    'Oxidized Cut Copper', 'Oxidized Cut Copper Slab', 'Oxidized Cut Copper Stairs',
    'Oxidized Lightning Rod',
    'Weathered Chiseled Copper', 'Weathered Copper Bars',
    'Weathered Copper Block', 'Weathered Copper Bulb',
    'Weathered Copper Chain', 'Weathered Copper Chest',
    'Weathered Copper Door', 'Weathered Copper Golem Statue',
    'Weathered Copper Grate', 'Weathered Copper Lantern',
    'Weathered Copper Trapdoor',
    'Weathered Cut Copper', 'Weathered Cut Copper Slab', 'Weathered Cut Copper Stairs',
    'Weathered Lightning Rod',
}


def normalize_filename(filename):
    """Extract base name from filename (remove extension, image sizing)."""
    base = filename.split('|')[0].strip()
    base = re.sub(r'\.(png|gif)$', '', base, flags=re.IGNORECASE)
    return base


def compute_simple_material(base_name):
    """Simple conversion: normalize spaces/underscores etc, to UPPER_SNAKE_CASE."""
    # Normalize underscores to spaces
    name = base_name.replace('_', ' ')
    # Handle apostrophe
    name = name.replace("'", "")
    # Strip leading "Inactive " prefix
    name = re.sub(r'^Inactive\s+', '', name, flags=re.IGNORECASE)
    # Strip version suffixes like "JE6 BE2", "BE1"
    name = re.sub(r'\s+[A-Z]+[0-9]+(?:\s+[A-Z]+[0-9]+)*$', '', name).strip()
    # Strip "Age N" suffixes
    name = re.sub(r'\s+Age\s+\d+$', '', name, flags=re.IGNORECASE).strip()
    # Strip trailing standalone numbers like "Turtle Egg 1" -> "Turtle Egg"
    name = re.sub(r'\s+\d+$', '', name).strip()
    # Strip parenthetical suffixes
    name = re.sub(r'\s*\([^)]*\)\s*$', '', name).strip()
    # Strip " Tip (D)" -> "Pointed Dripstone Tip" -> we also need to strip "Tip"
    # Actually for "Pointed Dripstone Tip" -> "POINTED_DRIPSTONE"
    name = re.sub(r'\s+Tip\s*$', '', name, flags=re.IGNORECASE).strip()
    # Strip "Axis Y" orientation marker
    name = re.sub(r'\s+Axis\s+[XYZ]$', '', name, flags=re.IGNORECASE).strip()
    # "Standing Sign" -> "Sign" (wiki uses "Standing Sign" to distinguish from hanging signs)
    name = re.sub(r'\bStanding Sign\b', 'Sign', name, flags=re.IGNORECASE).strip()

    material = name.upper().replace(' ', '_').replace('-', '_')
    return material


def try_block_of_x(base_name, known_materials):
    """Handle 'Block of X' -> 'X_BLOCK' conversion."""
    match = re.match(r'^Block of (.+)$', base_name, re.IGNORECASE)
    if not match:
        return None
    thing = match.group(1)
    material = thing.upper().replace(' ', '_') + '_BLOCK'
    # Lapis Lazuli special case
    material = material.replace('LAPIS_LAZULI_BLOCK', 'LAPIS_BLOCK')
    if material in known_materials:
        return material
    return None


def is_waxed_duplicate(filename_base, chinese_name):
    """Check if this duplicate entry is a waxed variant."""
    return '涂蜡' in chinese_name or '涂蜡的' in chinese_name or '涂蜡' in chinese_name


def is_weathered_duplicate(filename_base, chinese_name):
    """Check if filename already starts with Weathered."""
    return filename_base.startswith('Weathered')


def match_wiki_to_material(filename, chinese_name, known_materials, already_matched):
    """
    Match a wiki entry to a known material name.
    Returns (material_name, is_waxed) or None.
    """
    base = normalize_filename(filename)

    # Determine basic name for duplicate checking
    # Strip "Stripped_" or "Waxed_" or "Exposed_" / "Weathered_" / "Oxidized_" from the start
    # for duplicate detection purposes
    base_upper = base.upper().replace(' ', '_')

    # Is this a known duplicate filename?
    is_dup = base_upper in {d.upper().replace(' ', '_') for d in DUP_FILENAME_BASES}

    if is_dup:
        return resolve_duplicate(base, base_upper, chinese_name, known_materials, already_matched)

    # Strategy 1: Simple conversion
    material = compute_simple_material(base)
    if material in known_materials and material not in already_matched:
        return (material, False)

    # Strategy 2: "Block of X" inversion
    result = try_block_of_x(base, known_materials)
    if result and result not in already_matched:
        return (result, False)

    # Strategy 3: Try stripping parenthetical from base and retry
    cleaned = re.sub(r'\s*\([^)]*\)\s*$', '', base).strip()
    if cleaned != base:
        material = compute_simple_material(cleaned)
        if material in known_materials and material not in already_matched:
            return (material, False)

    # Strategy 4: Try with underscore normalization (for Nether_Wart_Age_3... -> Nether Wart)
    base_spaces = base.replace('_', ' ')
    material = compute_simple_material(base_spaces)
    if material in known_materials and material not in already_matched:
        return (material, False)

    # Strategy 5: If material ends with _BLOCK and not found, try without _BLOCK
    # Handles "Exposed Copper Block" -> EXPOSED_COPPER (not EXPOSED_COPPER_BLOCK)
    if material.endswith('_BLOCK'):
        no_block = material[:-6]
        if no_block in known_materials and no_block not in already_matched:
            return (no_block, False)

    # Strategy 6: Try stripping known material prefixes/suffixes
    # "Redstone Comparator" -> strip "Redstone_": COMPARATOR
    # "Redstone Repeater" -> REPEATER
    for prefix in ['REDSTONE_']:
        if material.startswith(prefix):
            stripped = material[len(prefix):]
            if stripped in known_materials and stripped not in already_matched:
                return (stripped, False)

    # Strategy 7: "Lapis Lazuli" -> "Lapis"
    if 'LAPIS_LAZULI' in material:
        no_lazuli = material.replace('LAPIS_LAZULI', 'LAPIS')
        if no_lazuli in known_materials and no_lazuli not in already_matched:
            return (no_lazuli, False)

    # Strategy 8: Try stripping "Crops" suffix (Wheat Crops -> Wheat)
    if material.endswith('_CROPS'):
        no_crops = material[:-6]
        if no_crops in known_materials and no_crops not in already_matched:
            return (no_crops, False)

    # Strategy 9: Common alias mappings
    alias_map = {
        'GRASS': 'SHORT_GRASS',
        'VINES': 'VINE',
    }
    if material in alias_map:
        aliased = alias_map[material]
        if aliased in known_materials and aliased not in already_matched:
            return (aliased, False)

    return None


def resolve_duplicate(base, base_upper, chinese_name, known_materials, already_matched):
    """Resolve duplicate filenames using Chinese name context."""
    # Case: Infested blocks (虫蚀) — no matching INFESTED materials in YAML
    if '虫蚀' in chinese_name:
        return None

    # Case: Melon/Pumpkin stem
    if base_upper.replace(' ', '_') == 'STEM':
        return None

    # Case: Petrified oak slab
    if base_upper.replace(' ', '_') == 'OAK_SLAB' and '石化' in chinese_name:
        if 'PETRIFIED_OAK_SLAB' in known_materials and 'PETRIFIED_OAK_SLAB' not in already_matched:
            return ('PETRIFIED_OAK_SLAB', False)
        return None

    # Case: Waxed copper — need to check if this is the secondary appearance
    # The wiki has: first occurrence = unwaxed or base, second = waxed
    # So the order is: plain -> waxed, exposed -> waxed_exposed, etc.
    # But weathered is tricky: first occurrence is waxed_weathered, second is just weathered
    # Let me handle by checking the Chinese name and checking if the non-prefixed version is already matched

    # Compute the base material name (as if non-waxed, non-prefixed)
    simple_material = compute_simple_material(base)

    if is_waxed_duplicate(base, chinese_name):
        # Compute WAXED_ variant
        # The base already has the prefix (e.g., "Weathered Copper Block")
        # WAXED_WEATHERED_COPPER
        waxed_material = 'WAXED_' + simple_material
        if waxed_material in known_materials and waxed_material not in already_matched:
            return (waxed_material, True)
        # Try without _BLOCK suffix (e.g., WAXED_EXPOSED_COPPER_BLOCK -> WAXED_EXPOSED_COPPER)
        if waxed_material.endswith('_BLOCK'):
            waxed_no_block = waxed_material[:-6]
            if waxed_no_block in known_materials and waxed_no_block not in already_matched:
                return (waxed_no_block, True)
        return None

    # Case: For things like "Weathered Copper Block.png" appearing a SECOND time
    # with Chinese "锈蚀的铜块" after WAXED_WEATHERED_COPPER was already matched
    # This means the second occurrence is just WEATHERED_COPPER
    # Check if the simple material is available (not yet matched)
    if simple_material in known_materials and simple_material not in already_matched:
        return (simple_material, False)

    # Try without _BLOCK suffix (e.g., EXPOSED_COPPER_BLOCK -> EXPOSED_COPPER)
    if simple_material.endswith('_BLOCK'):
        no_block = simple_material[:-6]
        if no_block in known_materials and no_block not in already_matched:
            return (no_block, False)

    # Case: For oxidized suffix: "Oxidized Copper Block.png" appearing a SECOND time as waxed/not
    # Try OXIDIZED_ prefix
    oxidized_material = 'OXIDIZED_' + simple_material
    # Actually, for Oxidized Copper Block, simple_material is already OXIDIZED_COPPER_BLOCK
    # so this doesn't apply. Let me handle generically:

    # For copper base entries that were already matched as plain COPPER_BLOCK
    # and now appear as waxed: the simple_material would be COPPER_BLOCK which is already matched
    # The first occurrence must have matched the simple_material
    # The WAXED_ handling above deals with the second occurrence

    # For non-waxed duplicates that are just different wiki order:
    # If simple material is already matched, it's a duplicate we don't need
    return None


# ─── Main Reorder Logic ──────────────────────────────────────────────────────

def should_skip_wiki_entry(chinese_name, computed_material):
    """Return True if this wiki entry should NOT be added as a new block."""
    # Technical/admin blocks
    for kw in ['命令方块', '结构方块', '结构空位', '拼图方块', '测试方块',
               '测试实例', '下界反应核', '不祥旗帜', '失水恶魂']:
        if kw in chinese_name:
            return True
    # Infested blocks
    if '虫蚀' in chinese_name:
        return True
    # Non-obtainable natural
    for kw in ['屏障', '基岩', '火', '熔岩', '水', '灵魂火', '细雪', '光源方块']:
        if kw in chinese_name:
            return True
    # Crop stages / stems / non-block items
    for kw in ['竹笋', '紫晶芽', '海龟蛋', '青蛙卵', '小麦植株', '高海草',
               '西瓜茎', '南瓜茎', '蘑菇柄']:
        if kw in chinese_name:
            return True
    # Material-level skip
    for mat in ['BEDROCK', 'BARRIER', 'LAVA', 'WATER', 'FIRE', 'SOUL_FIRE',
                'POWDER_SNOW', 'LIGHT_BLOCK', 'AMETHYST_CLUSTER',
                'LARGE_AMETHYST_BUD', 'MEDIUM_AMETHYST_BUD', 'SMALL_AMETHYST_BUD',
                'TURTLE_EGG', 'FROG_SPAWN', 'SNIFFER_EGG',
                'NETHER_REACTOR_CORE', 'OMINOUS_BANNER', 'DRIED_GHAST',
                'WHEAT_CROPS', 'TALL_SEAGRASS', 'MELON_STEM', 'PUMPKIN_STEM',
                'MUSHROOM_STEM', 'BAMBOO_SHOOT']:
        if computed_material == mat:
            return True
    return False


def main():
    print("Parsing block_list.yml...")
    wiki_entries = parse_wiki_entries(BLOCK_LIST_PATH)
    print(f"  Found {len(wiki_entries)} wiki entries")

    print("Parsing block-values.yml...")
    header_lines, parsed_blocks, tail_lines = parse_block_values(BLOCK_VALUES_PATH)
    known_materials = build_material_set(parsed_blocks)
    material_map = build_material_map(parsed_blocks)
    print(f"  Found {len(known_materials)} material entries")

    # Match wiki entries to materials
    matched_materials = set()
    wiki_matched_count = 0
    new_entries = []  # list of (material, chinese_name) to add with value 1
    skipped_entries = 0

    for idx, filename, cn_name in wiki_entries:
        result = match_wiki_to_material(filename, cn_name, known_materials, matched_materials)
        if result:
            material, is_waxed = result
            matched_materials.add(material)
            wiki_matched_count += 1
        else:
            # Check if this unmatched entry should be added as a new block
            base = normalize_filename(filename)
            new_material = compute_simple_material(base)
            if not should_skip_wiki_entry(cn_name, new_material):
                new_entries.append((new_material, cn_name))
            else:
                skipped_entries += 1

    print(f"  Matched: {wiki_matched_count}")
    print(f"  New entries to add: {len(new_entries)}")
    print(f"  Skipped (technical/non-block): {skipped_entries}")

    # ─── Rebuild blocks section ──────────────────────────────────────────
    new_blocks_lines = []

    # Second pass: output all entries in wiki order
    new_materials_from_wiki = set()
    already_output = set()  # Track what's already been written
    entry_count = 0

    for idx, filename, cn_name in wiki_entries:
        base = normalize_filename(filename)
        # Try to match against known materials that haven't been output yet
        result = match_wiki_to_material(filename, cn_name, known_materials, already_output)

        if result:
            material, _ = result
            # Matched to existing material
            entry = material_map[material]
            orig_line = entry['raw']
            comment_pos = orig_line.find('#')
            if comment_pos >= 0:
                leading = orig_line[:comment_pos].rstrip()
                new_line = f"{leading}  # {cn_name}\n"
            else:
                new_line = f"  {material}: {entry['value']}  # {cn_name}\n"
            new_blocks_lines.append(new_line)
            already_output.add(material)
            entry_count += 1
        else:
            new_material = compute_simple_material(base)
            if not should_skip_wiki_entry(cn_name, new_material) and new_material not in already_output:
                # Add as new entry with value 1
                new_line = f"  {new_material}: 1  # {cn_name}\n"
                new_blocks_lines.append(new_line)
                new_materials_from_wiki.add(new_material)
                already_output.add(new_material)
                entry_count += 1
            # Skipped — no output

    # Find materials in block-values that weren't matched by any wiki entry
    all_materials = {e['material'] for t, e in parsed_blocks if t == 'material'}
    truly_unmatched = all_materials - matched_materials - new_materials_from_wiki
    if truly_unmatched:
        # Sort unmatched entries alphabetically
        unmatched_sorted = sorted(
            (material_map[m] for m in truly_unmatched),
            key=lambda e: e['material']
        )
        # Interleave unmatched entries into the main list by alphabetical order
        unmatched_idx = 0
        interleaved_lines = []
        extract_pat = re.compile(r'^  ([A-Z_]+):')
        for line in new_blocks_lines:
            m = extract_pat.match(line)
            if m:
                line_material = m.group(1)
                while (unmatched_idx < len(unmatched_sorted)
                       and unmatched_sorted[unmatched_idx]['material'] < line_material):
                    entry = unmatched_sorted[unmatched_idx]
                    interleaved_lines.append(entry['raw'])
                    unmatched_idx += 1
                    entry_count += 1
            interleaved_lines.append(line)
        # Append any remaining unmatched entries (alphabetically last)
        while unmatched_idx < len(unmatched_sorted):
            entry = unmatched_sorted[unmatched_idx]
            interleaved_lines.append(entry['raw'])
            unmatched_idx += 1
            entry_count += 1
        new_blocks_lines = interleaved_lines

    print(f"  Total entries written: {entry_count}")

    # ─── Write Output ────────────────────────────────────────────────────
    # Update the header comment about sorting
    updated_header = []
    for line in header_lines:
        if '按 Minecraft 创造模式物品栏分类排序' in line:
            updated_header.append('# 方块按 Minecraft Wiki 方块列表排序\n')
        elif line.strip().startswith('#   1. 建筑方块'):
            updated_header.append('#   排序依据: Minecraft Wiki 方块列表\n')
        elif line.strip().startswith('#   2. 染色方块'):
            continue
        elif line.strip().startswith('#   3. 自然方块'):
            continue
        elif line.strip().startswith('#   4. 功能方块'):
            continue
        elif line.strip().startswith('#   5. 红石方块'):
            continue
        else:
            updated_header.append(line)

    with open(OUTPUT_PATH, 'w', encoding='utf-8') as f:
        f.writelines(updated_header)
        f.writelines(new_blocks_lines)
        f.writelines(tail_lines)

    print(f"\nDone! Output written to {OUTPUT_PATH}")


if __name__ == '__main__':
    main()