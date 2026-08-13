#!/usr/bin/env python3

import argparse

from affected_modules_core import calculate_output, changed_files, find_root


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="origin/main")
    parser.add_argument("--head", default="HEAD")
    parser.add_argument(
        "--mode",
        choices=["assemble", "build", "docker"],
        default="build"
    )
    parser.add_argument("--include-arch-test", action="store_true")
    parser.add_argument("--fallback-task", default="serviceCi")

    args = parser.parse_args()

    root = find_root()
    files = changed_files(args.base, args.head)

    output = calculate_output(
        root=root,
        files=files,
        mode=args.mode,
        include_arch_test=args.include_arch_test,
        fallback_task=args.fallback_task,
    )

    print(" ".join(output))


if __name__ == "__main__":
    main()
