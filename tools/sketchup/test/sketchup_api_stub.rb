# TEST DOUBLE ONLY - a minimal stand-in for the parts of the SketchUp Ruby API that
# aidf_build_model.rb uses, so the builder can be exercised without SketchUp.
# It emulates SketchUp's behaviour of flipping ground-plane faces to point down.
# The "model.skp" it writes is a stub: real .skp files are only produced by SketchUp.
class Numeric
  def mm
    to_f / 25.4
  end

  def to_mm
    to_f * 25.4
  end
end

module Geom
  class Vector3d
    attr_reader :x, :y, :z

    def initialize(x, y, z)
      @x = x
      @y = y
      @z = z
    end
  end
end

module Sketchup
  class Color
    def initialize(r, g, b)
      @rgb = [r, g, b]
    end
  end

  class Face
    attr_reader :pts, :normal, :height

    def initialize(pts)
      @pts = pts
      a, b, c = pts
      nz = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
      nz = -nz.abs if pts.all? { |p| p[2].abs < 1e-9 } # SketchUp points ground faces down
      @normal = Geom::Vector3d.new(0, 0, nz <=> 0)
    end

    def reverse!
      @normal = Geom::Vector3d.new(0, 0, -@normal.z)
    end

    def pushpull(dist)
      @height = dist * @normal.z
    end
  end

  class Entities
    attr_reader :items

    def initialize
      @items = []
    end

    def add_group
      g = Group.new
      @items << g
      g
    end

    def add_face(*pts)
      f = Face.new(pts)
      @items << f
      f
    end

    def length
      @items.length
    end

    def size
      @items.length
    end
  end

  class Group
    attr_accessor :name, :layer, :material
    attr_reader :entities

    def initialize
      @entities = Entities.new
      @attrs = {}
    end

    def set_attribute(d, k, v)
      @attrs["#{d}/#{k}"] = v
    end

    # [min, max] in inches
    def extent
      f = @entities.items.first
      z0 = f.pts.first[2]
      z1 = z0 + f.height
      xs = f.pts.map { |p| p[0] }
      ys = f.pts.map { |p| p[1] }
      [[xs.min, ys.min, [z0, z1].min], [xs.max, ys.max, [z0, z1].max]]
    end
  end

  class BoundingBox
    attr_reader :width, :height, :depth

    def initialize(w, h, d)
      @width = w
      @height = h
      @depth = d
    end
  end

  class Model
    attr_reader :active_entities, :options, :layers, :materials

    def initialize
      @active_entities = Entities.new
      @options = { 'UnitsOptions' => {} }
      @layers = Layers.new
      @materials = Materials.new
      @attrs = {}
    end

    def start_operation(*) = true
    def commit_operation = true
    def abort_operation = true

    def set_attribute(d, k, v)
      @attrs["#{d}/#{k}"] = v
    end

    def bounds
      ext = @active_entities.items.map(&:extent)
      lo = (0..2).map { |i| ext.map { |e| e[0][i] }.min }
      hi = (0..2).map { |i| ext.map { |e| e[1][i] }.max }
      BoundingBox.new(hi[0] - lo[0], hi[1] - lo[1], hi[2] - lo[2])
    end

    def save(path)
      header = "\xFF\xFE\xFF\x0E".b + 'SketchUp Model'.encode('UTF-16LE').b
      File.binwrite(path, header + "\nTEST STUB - NOT A REAL SKETCHUP MODEL\n".b)
      true
    end
  end

  class Layers
    def add(name) = name
  end

  class Materials
    class Mat
      attr_accessor :color
    end

    def initialize
      @m = {}
    end

    def [](n) = @m[n]

    def add(n)
      @m[n] = Mat.new
    end
  end

  @model = Model.new
  def self.active_model = @model
  def self.version = 'stub-0.0'
  def self.quit = nil
end
