#!/bin/sh
# TEST DOUBLE ONLY - pretends to be the SketchUp executable so the exporter's
# launch -> wait -> verify pipeline can be tested without SketchUp.
# Invoked exactly like SketchUp: fake_sketchup.sh -RubyStartup /path/build_model.rb
DIR=$(cd "$(dirname "$0")" && pwd)
[ "$1" = "-RubyStartup" ] || { echo "expected -RubyStartup" >&2; exit 2; }
exec ruby -r "$DIR/sketchup_api_stub.rb" "$2"
