"""Export real Git history as UTF-8 without shell-dependent redirection."""
from pathlib import Path
import subprocess


def main():
    root = Path(__file__).resolve().parents[1]
    payload = subprocess.check_output([
        "git", "-C", str(root), "-c", "i18n.logOutputEncoding=UTF-8",
        "log", "--all", "--date=iso", "--pretty=format:%h %ad %an %s",
    ])
    payload.decode("utf-8")
    target = root / "vcs/git_log.txt"
    target.parent.mkdir(exist_ok=True)
    target.write_bytes(payload)
    print("Saved vcs/git_log.txt: UTF-8, no BOM, real git log --all.")


if __name__ == "__main__":
    main()
