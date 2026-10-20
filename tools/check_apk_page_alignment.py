#!/usr/bin/env python3
"""Check an APK's 64-bit native libraries for 16 KB page alignment.

Usage: python3 tools/check_apk_page_alignment.py path/to/app.apk

Checks every arm64-v8a and x86_64 .so for aligned ELF PT_LOAD segments.
Uncompressed libraries must also start at a 16 KB boundary in the APK.
Compressed libraries are extracted on installation, so their ZIP offsets
do not need this alignment. Exit status is 0 on success and 1 on failure.
This verifies packaging and ELF layout; it does not replace device testing.
"""

import argparse
import struct
import sys
import zipfile


PAGE_SIZE = 16 * 1024
MACHINES = {"arm64-v8a": 183, "x86_64": 62}


class CheckError(ValueError):
    """An invalid or incompatible native library or APK entry."""


def check_elf(data, abi):
    if len(data) < 64 or data[:4] != b"\x7fELF":
        raise CheckError("missing or truncated ELF header")
    if data[4] != 2 or data[5] not in (1, 2) or data[6] != 1:
        raise CheckError("expected ELF64 with a supported byte order and version")

    byte_order = "<" if data[5] == 1 else ">"
    header = struct.unpack_from(byte_order + "HHIQQQIHHHHHH", data, 16)
    elf_type, machine, version, _, phoff, shoff, _, ehsize, phsize, phnum, shsize, _, _ = header
    if elf_type != 3 or machine != MACHINES[abi] or version != 1 or ehsize != 64:
        raise CheckError(f"invalid ELF shared-library header for {abi}")
    if phnum == 0xFFFF:  # ELF extended numbering stores the count in sh_info.
        if shsize < 64 or shoff < 64 or shoff + shsize > len(data):
            raise CheckError("invalid section header for extended program-header count")
        phnum = struct.unpack_from(byte_order + "IIQQQQIIQQ", data, shoff)[7]
    if not phnum or phsize < 56 or phoff < 64 or phoff + phnum * phsize > len(data):
        raise CheckError("missing or truncated ELF program-header table")

    alignments = []
    failures = []
    for index in range(phnum):
        segment = struct.unpack_from(byte_order + "IIQQQQQQ", data, phoff + index * phsize)
        kind, _, offset, address, _, file_size, memory_size, alignment = segment
        if kind != 1:  # PT_LOAD
            continue
        alignments.append(alignment)
        if file_size > memory_size or offset + file_size > len(data):
            failures.append(f"PT_LOAD[{index}] has invalid file/memory bounds")
        if alignment < PAGE_SIZE or alignment & (alignment - 1):
            failures.append(f"PT_LOAD[{index}] p_align=0x{alignment:x}, requires a power of two >=0x4000")
        elif offset % alignment != address % alignment:
            failures.append(f"PT_LOAD[{index}] file offset and virtual address disagree modulo p_align")
        if offset % PAGE_SIZE != address % PAGE_SIZE:
            failures.append(f"PT_LOAD[{index}] file offset and virtual address disagree modulo 16 KB")
    if not alignments:
        raise CheckError("ELF has no PT_LOAD segments")
    return min(alignments), failures


def zip_data_offset(apk_file, entry, apk_size):
    apk_file.seek(entry.header_offset)
    header = apk_file.read(30)
    if len(header) != 30:
        raise CheckError("truncated ZIP local header")
    fields = struct.unpack("<4s5H3I2H", header)
    if fields[0] != b"PK\x03\x04" or fields[3] != entry.compress_type:
        raise CheckError("invalid or inconsistent ZIP local header")
    offset = entry.header_offset + 30 + fields[9] + fields[10]
    if offset + entry.compress_size > apk_size:
        raise CheckError("ZIP library data extends beyond the APK")
    return offset


def check_apk(path):
    checked = 0
    failed = 0
    names = set()
    with open(path, "rb") as apk_file:
        apk_file.seek(0, 2)
        apk_size = apk_file.tell()
        with zipfile.ZipFile(apk_file) as apk:
            for entry in apk.infolist():
                parts = entry.filename.split("/")
                if len(parts) != 3 or parts[0] != "lib" or parts[1] not in MACHINES or not parts[2].endswith(".so"):
                    continue
                checked += 1
                errors = []
                details = []
                try:
                    if entry.filename in names:
                        raise CheckError("duplicate native library entry")
                    names.add(entry.filename)
                    if entry.flag_bits & 1:
                        raise CheckError("encrypted native library entry")
                    alignment, elf_errors = check_elf(apk.read(entry), parts[1])
                    errors.extend(elf_errors)
                    details.append(f"minimum PT_LOAD alignment: 0x{alignment:x}")
                    offset = zip_data_offset(apk_file, entry, apk_size)
                    if entry.compress_type == zipfile.ZIP_STORED:
                        details.append(f"ZIP data offset: 0x{offset:x}")
                        if offset % PAGE_SIZE:
                            errors.append(f"uncompressed library ZIP data offset 0x{offset:x} is not 16 KB aligned")
                    else:
                        details.append("compressed; ZIP alignment not required")
                except (CheckError, zipfile.BadZipFile, RuntimeError, NotImplementedError, struct.error) as error:
                    errors.append(str(error))
                status = "FAIL" if errors else "PASS"
                print(f"{status} {entry.filename}" + (f" ({'; '.join(details)})" if details else ""))
                for error in errors:
                    print(f"  {error}")
                failed += bool(errors)
    if not checked:
        print("FAIL: APK contains no arm64-v8a or x86_64 native libraries.")
        return 1
    print(f"Checked {checked} 64-bit native libraries: {checked - failed} passed, {failed} failed.")
    return int(bool(failed))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("apk", help="APK to validate")
    args = parser.parse_args()
    try:
        return check_apk(args.apk)
    except (OSError, ValueError, zipfile.BadZipFile, RuntimeError, NotImplementedError, struct.error) as error:
        print(f"FAIL: cannot validate {args.apk}: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
