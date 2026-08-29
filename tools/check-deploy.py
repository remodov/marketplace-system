#!/usr/bin/env python3
"""Проверка того, что сервис можно выкатывать.

Скрипт смотрит на манифесты, образ и пайплайн и ругается на вещи, из-за которых
выкат ломается в проде, а не на стенде: под без проб кластер считает живым
мгновенно, под без лимитов съедает узел, образ с тегом latest невозможно
откатить, контейнер от root — лишний риск.

Запуск: python3 tools/check-deploy.py
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
K8S = ROOT / "deploy" / "k8s"
WORKFLOW = ROOT / ".github" / "workflows" / "ci.yml"
DOCKERFILES = ["services/catalog-starter/Dockerfile"]

problems: list[str] = []


def fail(where: str, what: str) -> None:
    problems.append(f"{where}: {what}")


def check_manifest(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    where = str(path.relative_to(ROOT))

    if "kind: Deployment" not in text:
        return

    for probe in ("readinessProbe", "livenessProbe"):
        if probe not in text:
            fail(where, f"нет {probe} — кластер не узнает, готов ли под")

    if "resources:" not in text:
        fail(where, "нет resources — под без лимитов может съесть узел")
    else:
        for section in ("requests:", "limits:"):
            if section not in text:
                fail(where, f"в resources нет {section}")

    if "runAsNonRoot: true" not in text:
        fail(where, "контейнер не помечен runAsNonRoot")

    image = re.search(r"image:\s*(\S+)", text)
    if not image:
        fail(where, "не указан image")
    elif ":" not in image.group(1).rsplit("/", 1)[-1]:
        fail(where, f"у образа {image.group(1)} нет тега — откатиться будет нечем")
    elif image.group(1).endswith(":latest"):
        fail(where, "тег latest: две выкатки дадут разные образы под одним именем")

    if "preStop" not in text:
        fail(where, "нет preStop — под уйдёт из балансировщика позже, чем перестанет отвечать")


def check_dockerfile(path: Path) -> None:
    if not path.is_file():
        fail(str(path), "файла нет")
        return
    text = path.read_text(encoding="utf-8")
    where = str(path.relative_to(ROOT))

    if "USER " not in text:
        fail(where, "нет USER — контейнер побежит от root")
    if text.count("FROM ") < 2:
        fail(where, "сборка и запуск в одном слое — в образ уедут gradle и исходники")
    if re.search(r"FROM\s+\S+:latest", text):
        fail(where, "базовый образ с тегом latest")


def check_workflow(path: Path) -> None:
    if not path.is_file():
        fail(str(path), "нет пайплайна")
        return
    text = path.read_text(encoding="utf-8")
    where = str(path.relative_to(ROOT))

    if "test" not in text:
        fail(where, "пайплайн ничего не тестирует")
    if "pull_request" not in text:
        fail(where, "пайплайн не запускается на pull request — сломанное вливается молча")
    if "check-deploy.py" not in text:
        fail(where, "пайплайн не проверяет манифесты")


def main() -> int:
    manifests = sorted(K8S.glob("*.yaml")) if K8S.is_dir() else []
    if not manifests:
        fail("deploy/k8s", "манифестов нет")
    for manifest in manifests:
        check_manifest(manifest)

    for dockerfile in DOCKERFILES:
        check_dockerfile(ROOT / dockerfile)

    check_workflow(WORKFLOW)

    if problems:
        print(f"Проблем: {len(problems)}")
        for problem in problems:
            print("  ✗", problem)
        return 1

    print(f"Проверено манифестов: {len(manifests)}, образов: {len(DOCKERFILES)}. Замечаний нет.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
