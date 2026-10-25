# AI Interior Design Factory - SketchUp model builder.
#
# Runs INSIDE SketchUp (Ruby API) and creates real SketchUp entities from the
# factory's build manifest, then saves a native .skp with Model#save.
# Started either by the exporter agent (SketchUp -RubyStartup <this file>)
# or manually from Window > Ruby Console:  load 'C:/path/to/build_model.rb'
#
# Every box in the manifest is in millimetres, origin at the room's inner
# south-west floor corner, +X = room length, +Y = room width, +Z up.
require 'json'

module AIDF
  module Builder
    SCHEMA = '1.0'.freeze

    def self.run(job_path)
      job = JSON.parse(File.read(job_path))
      result_path = job['resultPath']
      begin
        manifest = JSON.parse(File.read(job['manifestPath']))
        raise "Unsupported manifest schema #{manifest['schemaVersion']}" unless manifest['schemaVersion'] == SCHEMA

        model = Sketchup.active_model
        model.start_operation('AIDF: build room', true)
        model.options['UnitsOptions']['LengthUnit'] = 2 # millimetres
        ents = model.active_entities
        built = 0
        manifest['boxes'].each do |b|
          build_box(model, ents, b)
          built += 1
        end
        model.set_attribute('aidf', 'schemaVersion', SCHEMA)
        model.set_attribute('aidf', 'sessionId', job['sessionId'].to_s)
        model.set_attribute('aidf', 'designVersion', job['designVersion'].to_i)
        model.commit_operation

        saved = model.save(job['outputPath'])
        bb = model.bounds
        write(result_path,
              status: saved ? 'SAVED' : 'SAVE_FAILED',
              outputPath: job['outputPath'],
              sketchupVersion: Sketchup.version,
              boxesBuilt: built,
              entityCount: ents.length,
              boundsMm: { x: bb.width.to_mm.round(1), y: bb.height.to_mm.round(1), z: bb.depth.to_mm.round(1) })
      rescue StandardError => e
        model.abort_operation rescue nil
        write(result_path, status: 'ERROR', error: e.message, backtrace: (e.backtrace || []).first(5))
      ensure
        Sketchup.quit if job['quitWhenDone']
      end
    end

    def self.build_box(model, ents, b)
      x0, y0, z0 = b['min'].map { |v| v.to_f.mm }
      x1, y1, z1 = b['max'].map { |v| v.to_f.mm }
      group = ents.add_group
      group.name = "#{b['name']} [#{b['id']}]"
      group.layer = model.layers.add(b['tag'])
      face = group.entities.add_face([x0, y0, z0], [x1, y0, z0], [x1, y1, z0], [x0, y1, z0])
      face.reverse! if face.normal.z < 0
      face.pushpull(z1 - z0)
      if b['color']
        mat = model.materials[b['material']] || model.materials.add(b['material'])
        mat.color = Sketchup::Color.new(*b['color'])
        group.material = mat
      end
      group.set_attribute('aidf', 'id', b['id'])
    end

    def self.write(path, hash)
      File.write(path, JSON.generate(hash))
    end
  end
end
