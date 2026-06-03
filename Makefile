.PHONY: help test lint
help:
	@echo "make test  - run unit tests"
	@echo "make lint  - run ruff (if installed)"
test:
	python -m unittest discover -s tests -v
lint:
	@command -v ruff >/dev/null 2>&1 && ruff check remit_core || echo "ruff not installed; skipping"
