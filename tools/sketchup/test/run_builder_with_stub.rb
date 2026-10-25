# Usage: ruby run_builder_with_stub.rb <export-dir containing job.json + build_model.rb>
# Loads the API stub, then the generated build_model.rb exactly as SketchUp would.
require_relative 'sketchup_api_stub'
dir = ARGV.fetch(0)
load File.join(dir, 'build_model.rb')
puts File.read(File.join(dir, 'sketchup_result.json'))
