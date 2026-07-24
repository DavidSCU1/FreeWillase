import argparse
import json
import os
import sys
import time
import traceback
from pathlib import Path
import types


PROJECT_ROOT = Path(__file__).resolve().parents[1]
RUNTIME_ROOT = Path(__file__).resolve().parent

if str(RUNTIME_ROOT) not in sys.path:
    sys.path.insert(0, str(RUNTIME_ROOT))


def ensure_runtime_modules_package():
    modules_dir = RUNTIME_ROOT / "modules"
    existing = sys.modules.get("modules")
    if existing is None or not getattr(existing, "__path__", None):
        pkg = types.ModuleType("modules")
        pkg.__path__ = [str(modules_dir)]
        sys.modules["modules"] = pkg
    else:
        paths = list(getattr(existing, "__path__", []))
        if str(modules_dir) not in paths:
            existing.__path__ = [str(modules_dir)] + paths


def build_logger(log_path: Path):
    def log(message: str):
        timestamp = time.strftime("%H:%M:%S")
        line = f"[{timestamp}] {str(message).rstrip()}"
        print(line, flush=True)
        with log_path.open("a", encoding="utf-8") as handle:
            handle.write(line + "\n")

    return log


def write_result(result_path: Path, payload: dict):
    with result_path.open("w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=2)


def collect_outputs(task_dir: Path):
    workdir = task_dir / "input"
    three_d_dir = workdir / "3d_structures"

    pdb_content = ""

    if three_d_dir.exists():
        pdb_files = sorted(item for item in three_d_dir.iterdir() if item.suffix.lower() == ".pdb")
        if pdb_files:
            pdb_content = pdb_files[0].read_text(encoding="utf-8")
    return pdb_content


def run_task(task_dir: Path, payload: dict):
    ensure_runtime_modules_package()

    from modules.pipeline import run_pipeline
    from modules.env_loader import load_env

    log_path = task_dir / "process.log"
    result_path = task_dir / "result.json"
    logger = build_logger(log_path)

    for env_root in [PROJECT_ROOT, RUNTIME_ROOT]:
        for env_name in [".env.local", ".env"]:
            env_path = env_root / env_name
            if env_path.exists():
                try:
                    load_env(str(env_path))
                    logger(f"Loaded environment file: {env_path}")
                except Exception as exc:
                    logger(f"Environment load failed for {env_path}: {exc}")

    sequence = (payload.get("sequence") or "").strip()
    if not sequence:
        raise ValueError("Missing protein sequence")

    if sequence.startswith(">"):
        lines = sequence.splitlines()
        if len(lines) > 1:
            sequence = "".join(lines[1:]).strip()

    fasta_path = task_dir / "input.fasta"
    fasta_path.write_text(f">task\n{sequence}\n", encoding="utf-8")

    target_chains = payload.get("targetChains")
    try:
        target_chains = int(target_chains) if target_chains not in (None, "", 0) else None
    except Exception:
        target_chains = None

    use_igpu = bool(payload.get("useIgpu", False))
    backend = payload.get("backend") or ("auto" if use_igpu else "cpu")
    ext_env_name = None

    logger("==== FreeWillase MiniFold Runtime Started ====")
    logger(f"Sequence length: {len(sequence)}")
    logger(f"Target chains: {target_chains or 'auto'}")
    logger(f"Acceleration: {'enabled' if use_igpu else 'disabled'}")
    logger(f"Backend: {backend}")

    write_result(result_path, {"status": "running"})

    original_cwd = Path.cwd()
    try:
        os.chdir(RUNTIME_ROOT)
        run_pipeline(
            str(fasta_path),
            str(task_dir),
            payload.get("envText") or "",
            use_igpu,
            target_chains=target_chains,
            backend=backend,
            log_callback=logger,
        )
    finally:
        os.chdir(original_cwd)

    pdb_content = collect_outputs(task_dir)
    if not pdb_content:
        raise RuntimeError("Pipeline finished but no PDB structure was generated.")

    write_result(
        result_path,
        {
            "status": "success",
            "pdb": pdb_content,
        },
    )
    logger("==== FreeWillase MiniFold Runtime Finished ====")


def main():
    parser = argparse.ArgumentParser(description="FreeWillase embedded MiniFold worker")
    parser.add_argument("--task-dir", required=True, help="Task directory")
    parser.add_argument("--payload", required=True, help="Payload JSON path")
    args = parser.parse_args()

    task_dir = Path(args.task_dir).resolve()
    payload_path = Path(args.payload).resolve()
    task_dir.mkdir(parents=True, exist_ok=True)

    try:
        payload = json.loads(payload_path.read_text(encoding="utf-8"))
        run_task(task_dir, payload)
    except Exception as exc:
        log_path = task_dir / "process.log"
        logger = build_logger(log_path)
        logger(f"[ERROR] {exc}")
        logger(traceback.format_exc())
        write_result(
            task_dir / "result.json",
            {
                "status": "failed",
                "error": str(exc),
            },
        )
        sys.exit(1)


if __name__ == "__main__":
    main()
