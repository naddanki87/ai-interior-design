# Modular kitchen - SketchUp model of the reference photo (reference.jpg).
#
# Runs INSIDE SketchUp (Ruby API). Open SketchUp with a new, empty model, then
# Window > Ruby Console and run:
#   load 'C:/path/to/modular_kitchen.rb'
# It builds the whole kitchen as one "Modular Kitchen" group (undo = one step),
# adds a "Reference view" scene matching the photo and saves
# modular_kitchen.skp next to this script.
#
# Layout follows the photo exactly: L-shaped back run (hob + hood on the left,
# open dark shelves, glossy taupe uppers, window with roman blinds, dishwasher,
# sink), door on the right wall, black fridge + tall appliance/basket unit on
# the right, peninsula with fluted wooden front and marble end panel, lit
# wooden display shelf, curved wooden ceiling bulkhead with 3 glass pendants,
# recessed downlights, glossy marble floor and rug.
#
# The photo carries no measurements, so sizes are standard modular-kitchen
# sizes scaled to the photo's proportions. Every size is in millimetres and
# the main ones are the constants below; change them to your site sizes.
# Coordinates: origin at the inner front-left floor corner, +X along the back
# wall (left to right), +Y towards the back wall, +Z up.
module ModularKitchen
  W = 4000            # room width, left wall to right wall
  D = 4200            # room depth, origin to back wall
  FRONT = -600        # the room continues 600 mm in front of the origin (camera side)
  H = 2850            # floor to ceiling
  WALL = 115          # wall thickness

  BASE_DEPTH = 600
  COUNTER_TOP = 900   # back counter top
  UPPER_BOTTOM = 1650 # underside of wall cabinets
  UPPER_DEPTH = 350
  WIN_X0 = 1950       # window opening in the back wall
  WIN_X1 = 3150
  WIN_Z0 = 1000
  WIN_Z1 = 2600
  DOOR_Y0 = 2680      # door opening in the right wall
  DOOR_Y1 = 3530
  DOOR_H = 2100

  # name => [r, g, b] or [r, g, b, alpha]
  MATERIALS = {
    'Wall paint'          => [233, 229, 222],
    'Ceiling white'       => [248, 248, 246],
    'Floor marble gloss'  => [238, 232, 222],
    'Cabinet gloss taupe' => [198, 190, 181],
    'Counter marble'      => [226, 233, 226],
    'Backsplash aqua marble' => [186, 214, 203],
    'Warm oak'            => [181, 126, 76],
    'Oak slat'            => [158, 104, 62],
    'Door walnut'         => [152, 106, 70],
    'LED warm'            => [255, 204, 128],
    'Plinth dark'         => [70, 62, 54],
    'Black gloss'         => [24, 24, 27],
    'Shelf dark'          => [44, 41, 38],
    'Stainless'           => [184, 186, 188],
    'Hob glass'           => [16, 16, 18],
    'Window frame grey'   => [128, 128, 126],
    'Glass'               => [190, 215, 225, 0.30],
    'Blind fabric'        => [224, 204, 172],
    'Pendant amber glass' => [226, 178, 104, 0.45],
    'Bulb glow'           => [255, 214, 140],
    'Wicker'              => [168, 128, 80],
    'Rug'                 => [128, 116, 102],
    'Plant green'         => [74, 112, 52],
    'Pot white'           => [240, 238, 234],
    'Ceramic cream'       => [232, 222, 204],
    'Orange'              => [236, 140, 30],
    'Chopping board'      => [196, 150, 98]
  }.freeze

  def self.design(b)
    structure(b)
    back_run(b)
    right_wall(b)
    peninsula(b)
    display_shelf(b)
    ceiling_features(b)
    decor(b)
  end

  def self.structure(b)
    b.tag('Structure')
    b.floor('Floor', 'Floor marble gloss', 0, FRONT, W, D, 800)
    b.box('Left wall', 'Wall paint', -WALL, FRONT, 0, 0, D, H)
    # Back wall, split around the window opening.
    b.box('Back wall left', 'Wall paint', -WALL, D, 0, WIN_X0, D + WALL, H)
    b.box('Back wall right', 'Wall paint', WIN_X1, D, 0, W + WALL, D + WALL, H)
    b.box('Back wall below window', 'Wall paint', WIN_X0, D, 0, WIN_X1, D + WALL, WIN_Z0)
    b.box('Back wall above window', 'Wall paint', WIN_X0, D, WIN_Z1, WIN_X1, D + WALL, H)
    # Right wall, split around the door opening.
    b.box('Right wall front', 'Wall paint', W, FRONT, 0, W + WALL, DOOR_Y0, H)
    b.box('Right wall back', 'Wall paint', W, DOOR_Y1, 0, W + WALL, D, H)
    b.box('Right wall above door', 'Wall paint', W, DOOR_Y0, DOOR_H, W + WALL, DOOR_Y1, H)

    b.tag('Ceiling')
    b.box('Ceiling', 'Ceiling white', -WALL, FRONT, H, W + WALL, D + WALL, H + 50)

    b.tag('Window')
    fy0 = D + 40
    fy1 = D + 80
    b.box('Window frame bottom', 'Window frame grey', WIN_X0, fy0, WIN_Z0, WIN_X1, fy1, WIN_Z0 + 60)
    b.box('Window frame top', 'Window frame grey', WIN_X0, fy0, WIN_Z1 - 60, WIN_X1, fy1, WIN_Z1)
    b.box('Window frame left', 'Window frame grey', WIN_X0, fy0, WIN_Z0, WIN_X0 + 60, fy1, WIN_Z1)
    b.box('Window frame right', 'Window frame grey', WIN_X1 - 60, fy0, WIN_Z0, WIN_X1, fy1, WIN_Z1)
    mid = (WIN_X0 + WIN_X1) / 2
    b.box('Window mullion', 'Window frame grey', mid - 30, fy0, WIN_Z0, mid + 30, fy1, WIN_Z1)
    b.box('Window glass', 'Glass', WIN_X0 + 60, fy0 + 15, WIN_Z0 + 60, WIN_X1 - 60, fy0 + 25, WIN_Z1 - 60)
    b.box('Window sill', 'Counter marble', WIN_X0, D, WIN_Z0 - 20, WIN_X1, fy0, WIN_Z0)
    # Two roman blinds, half lowered, with fold lines.
    [[WIN_X0 + 30, mid - 10], [mid + 10, WIN_X1 - 30]].each_with_index do |(x0, x1), i|
      b.box("Roman blind #{i + 1}", 'Blind fabric', x0, D + 10, 2050, x1, D + 22, WIN_Z1 - 20)
      [2050, 2230, 2400].each do |z|
        b.box("Roman blind #{i + 1} fold", 'Blind fabric', x0, D + 4, z, x1, D + 10, z + 25)
      end
    end
  end

  def self.back_run(b)
    y_front = D - BASE_DEPTH
    dw_x0 = 2220
    dw_x1 = 2820
    run_x1 = 3900
    carcass_top = COUNTER_TOP - 40

    b.tag('Base cabinets')
    b.box('Back run plinth', 'Plinth dark', 0, y_front + 60, 0, run_x1, D, 100)
    b.box('Back run plinth LED', 'LED warm', 0, y_front + 55, 0, run_x1, y_front + 65, 15)
    b.box('Back run carcass left', 'Cabinet gloss taupe', 0, y_front + 20, 100, dw_x0, D, carcass_top)
    b.box('Back run carcass right', 'Cabinet gloss taupe', dw_x1, y_front + 20, 100, run_x1, D, carcass_top)
    # Fronts: [x0, x1, [z splits]] - drawers/doors as in the photo.
    fronts = [
      [0, 600, []],                     # door beside the left wall
      [600, 1200, [350, 600]],          # 3 drawers under the hob
      [1200, 1700, []],                 # door
      [1700, dw_x0, [480]],             # 2 deep drawers left of the dishwasher
      [dw_x1, 3360, [700]],             # top drawer + door
      [3360, run_x1, [700]]             # top drawer + door (sink unit)
    ]
    fronts.each_with_index do |(x0, x1, splits), i|
      edges = [100] + splits + [carcass_top]
      edges.each_cons(2).with_index do |(z0, z1), j|
        b.box("Base front #{i + 1}.#{j + 1}", 'Cabinet gloss taupe', x0 + 2, y_front, z0 + 2, x1 - 2, y_front + 20, z1 - 2)
      end
    end

    b.tag('Appliances')
    b.box('Dishwasher', 'Stainless', dw_x0 + 2, y_front - 5, 100, dw_x1 - 2, D, carcass_top)
    b.box('Dishwasher handle', 'Stainless', dw_x0 + 80, y_front - 30, carcass_top - 70, dw_x1 - 80, y_front - 5, carcass_top - 50)
    b.box('Gas hob', 'Hob glass', 300, y_front + 100, COUNTER_TOP, 1000, D - 50, COUNTER_TOP + 6)
    [[450, y_front + 220], [850, y_front + 220], [450, D - 170], [850, D - 170]].each_with_index do |(x, y), i|
      b.cyl("Hob burner #{i + 1}", 'Stainless', x, y, COUNTER_TOP + 6, 55, 20)
    end
    # Hood: black canopy + chimney.
    b.box('Hood canopy', 'Black gloss', 300, D - 500, UPPER_BOTTOM, 1000, D, UPPER_BOTTOM + 110)
    b.box('Hood chimney', 'Black gloss', 500, D - 260, UPPER_BOTTOM + 110, 800, D, 1950)
    b.box('Sink', 'Stainless', 3250, y_front + 100, COUNTER_TOP, 3750, D - 100, COUNTER_TOP + 3)
    b.cyl('Faucet', 'Stainless', 3500, D - 60, COUNTER_TOP, 18, 320)
    b.box('Faucet spout', 'Stainless', 3485, D - 260, COUNTER_TOP + 290, 3515, D - 60, COUNTER_TOP + 320)

    b.tag('Countertops')
    b.box('Back counter', 'Counter marble', 0, y_front - 20, carcass_top, run_x1, D, COUNTER_TOP)
    # Aqua marble backsplash, counter to wall cabinets, below the window sill.
    b.box('Backsplash left', 'Backsplash aqua marble', 0, D - 15, COUNTER_TOP, WIN_X0, D, UPPER_BOTTOM)
    b.box('Backsplash under window', 'Backsplash aqua marble', WIN_X0, D - 15, COUNTER_TOP, WIN_X1, D, WIN_Z0 - 20)
    b.box('Backsplash right', 'Backsplash aqua marble', WIN_X1, D - 15, COUNTER_TOP, run_x1, D, UPPER_BOTTOM)

    b.tag('Wall cabinets')
    uy = D - UPPER_DEPTH
    # Open dark shelving around the hood (left of the back wall).
    b.box('Open shelf back panel', 'Shelf dark', 0, D - 18, UPPER_BOTTOM, 1200, D - 15, H)
    b.box('Open shelf tower left', 'Shelf dark', 0, uy, UPPER_BOTTOM, 300, uy + 20, UPPER_BOTTOM + 20)
    b.box('Open shelf tower right', 'Shelf dark', 1000, uy, UPPER_BOTTOM, 1200, uy + 20, UPPER_BOTTOM + 20)
    [[0, 25], [275, 300], [1000, 1025], [1175, 1200]].each_with_index do |(x0, x1), i|
      b.box("Open shelf side #{i + 1}", 'Shelf dark', x0, uy, UPPER_BOTTOM, x1, D - 18, H)
    end
    [UPPER_BOTTOM, 1950, 2250, 2550].each do |z|
      [[25, 275], [1025, 1175]].each do |x0, x1|
        b.box('Open shelf board', 'Shelf dark', x0, uy, z, x1, D - 18, z + 25)
        b.box('Open shelf LED', 'LED warm', x0, uy + 20, z - 8, x1, uy + 35, z)
      end
    end
    [2250, 2550].each do |z|
      b.box('Open shelf board over hood', 'Shelf dark', 300, uy, z, 1000, D - 18, z + 25)
      b.box('Open shelf LED over hood', 'LED warm', 300, uy + 20, z - 8, 1000, uy + 35, z)
    end
    b.box('Open shelf top', 'Shelf dark', 0, uy, H - 25, 1200, D - 18, H)
    # Glossy taupe wall cabinets, 2 doors x 2 tiers, either side of the window.
    [[1200, WIN_X0], [WIN_X1, run_x1]].each_with_index do |(x0, x1), i|
      b.box("Wall cabinet carcass #{i + 1}", 'Cabinet gloss taupe', x0, uy + 20, UPPER_BOTTOM, x1, D, H)
      xm = (x0 + x1) / 2
      [[UPPER_BOTTOM, 2300], [2300, H]].each do |z0, z1|
        [[x0, xm], [xm, x1]].each do |dx0, dx1|
          b.box("Wall cabinet #{i + 1} door", 'Cabinet gloss taupe', dx0 + 2, uy, z0 + 2, dx1 - 2, uy + 20, z1 - 2)
        end
      end
      b.box("Wall cabinet #{i + 1} LED", 'LED warm', x0, uy + 30, UPPER_BOTTOM - 8, x1, uy + 50, UPPER_BOTTOM)
    end
  end

  def self.right_wall(b)
    b.tag('Door')
    b.box('Door leaf', 'Door walnut', W + 20, DOOR_Y0 + 40, 0, W + 60, DOOR_Y1 - 40, DOOR_H - 40)
    b.box('Door frame near', 'Door walnut', W - 15, DOOR_Y0 - 40, 0, W + WALL, DOOR_Y0 + 40, DOOR_H)
    b.box('Door frame far', 'Door walnut', W - 15, DOOR_Y1 - 40, 0, W + WALL, DOOR_Y1 + 40, DOOR_H)
    b.box('Door frame head', 'Door walnut', W - 15, DOOR_Y0 - 40, DOOR_H - 40, W + WALL, DOOR_Y1 + 40, DOOR_H + 30)
    b.box('Door handle', 'Stainless', W - 5, DOOR_Y0 + 80, 1000, W + 20, DOOR_Y0 + 220, 1030)

    b.tag('Appliances')
    fx = W - 650
    f0 = 350
    f1 = 1250
    fm = (f0 + f1) / 2
    # Black French-door fridge with freezer drawer, black loft above.
    b.box('Fridge body', 'Black gloss', fx + 20, f0, 0, W, f1, 1900)
    b.box('Fridge door near', 'Black gloss', fx, f0 + 2, 700, fx + 20, fm - 3, 1898)
    b.box('Fridge door far', 'Black gloss', fx, fm + 3, 700, fx + 20, f1 - 2, 1898)
    b.box('Freezer drawer', 'Black gloss', fx, f0 + 2, 40, fx + 20, f1 - 2, 695)
    b.box('Fridge handle near', 'Stainless', fx - 30, fm - 40, 1000, fx, fm - 20, 1700)
    b.box('Fridge handle far', 'Stainless', fx - 30, fm + 20, 1000, fx, fm + 40, 1700)
    b.box('Freezer handle', 'Stainless', fx - 30, fm - 250, 600, fx, fm + 250, 620)
    b.box('Fridge loft', 'Black gloss', fx + 50, f0, 1950, W, f1, H)

    b.tag('Tall unit')
    f0 = 350
    # Tall unit between the camera and the fridge: baskets, oven, microwave, open top.
    tx = W - 650
    t0 = -300
    t1 = f0
    b.box('Tall unit side front', 'Black gloss', tx, t0, 0, W, t0 + 20, H)
    b.box('Tall unit side back', 'Black gloss', tx, t1 - 20, 0, W, t1, H)
    b.box('Tall unit back', 'Shelf dark', W - 20, t0 + 20, 0, W, t1 - 20, H)
    [0, 900, 1600, 2000, H - 25].each do |z|
      b.box('Tall unit shelf', 'Black gloss', tx, t0 + 20, z, W - 20, t1 - 20, z + 25)
    end
    b.box('Wicker basket lower', 'Wicker', tx + 40, t0 + 40, 60, W - 40, t1 - 40, 440)
    b.box('Wicker basket upper', 'Wicker', tx + 40, t0 + 40, 480, W - 40, t1 - 40, 860)
    b.box('Built-in oven', 'Black gloss', tx + 20, t0 + 30, 960, W - 20, t1 - 30, 1560)
    b.box('Oven window', 'Glass', tx + 10, t0 + 110, 1080, tx + 20, t1 - 110, 1430)
    b.box('Oven handle', 'Stainless', tx - 20, t0 + 90, 1480, tx + 20, t1 - 90, 1500)
    b.box('Microwave', 'Stainless', tx + 20, t0 + 50, 1625, W - 20, t1 - 50, 1950)
    b.box('Microwave door', 'Black gloss', tx + 10, t0 + 70, 1650, tx + 20, t1 - 200, 1925)
    b.box('Tall unit LED', 'LED warm', tx + 20, t0 + 20, 2000 - 8, tx + 40, t1 - 20, 2000)
  end

  def self.peninsula(b)
    x1 = 1900
    y0 = 700
    y1 = 1500
    top0 = 880
    top1 = 920

    b.tag('Peninsula')
    b.box('Peninsula plinth', 'Plinth dark', 0, y0 + 40, 0, x1, y1, 80)
    b.box('Peninsula plinth LED', 'LED warm', 0, y0 + 35, 0, x1, y0 + 45, 15)
    b.box('Peninsula body', 'Warm oak', 0, y0, 80, x1, y1, top0)
    # Fluted front: vertical oak slats.
    x = 20
    while x + 25 <= x1 - 10
      b.box('Peninsula slat', 'Oak slat', x, y0 - 25, 80, x + 25, y0, top0 - 15)
      x += 50
    end
    b.box('Peninsula top LED', 'LED warm', 0, y0 - 40, top0 - 15, x1, y0 - 30, top0)
    b.box('Peninsula end panel', 'Backsplash aqua marble', x1, y0 - 60, 0, x1 + 50, y1, top0)
    b.box('Peninsula end LED', 'LED warm', x1, y0 - 60, 0, x1 + 50, y0 - 40, 15)
    b.box('Peninsula counter', 'Backsplash aqua marble', 0, y0 - 60, top0, x1 + 50, y1, top1)
  end

  def self.display_shelf(b)
    x1 = 550
    y0 = 1500
    y1 = 1850
    top = 2600

    b.tag('Display shelf')
    b.box('Display shelf side left', 'Warm oak', 0, y0, 0, 25, y1, top)
    b.box('Display shelf back', 'Warm oak', 25, y1 - 20, 0, x1, y1, top)
    [940, 1280, 1620, 1960, 2300].each_with_index do |z, i|
      b.box("Display shelf #{i + 1}", 'Warm oak', 25, y0, z, x1, y1 - 20, z + 30)
      b.box("Display shelf #{i + 1} LED", 'LED warm', 25, y0 + 15, z - 8, x1, y0 + 30, z)
    end
    # Decor on the shelves: vases, bowls and small plants.
    b.cyl('Shelf vase', 'Ceramic cream', 150, 1680, 970, 55, 230)
    b.cyl('Shelf vase', 'Ceramic cream', 380, 1680, 970, 45, 160)
    b.cyl('Shelf pot', 'Pot white', 160, 1680, 1310, 60, 110)
    b.sphere('Shelf plant', 'Plant green', 160, 1680, 1480, 110)
    b.cyl('Shelf vase', 'Ceramic cream', 400, 1680, 1310, 50, 200)
    b.cyl('Shelf bowl', 'Ceramic cream', 270, 1680, 1650, 90, 60)
    b.cyl('Shelf vase', 'Ceramic cream', 150, 1680, 1990, 50, 220)
    b.cyl('Shelf pot', 'Pot white', 380, 1680, 1990, 60, 110)
    b.sphere('Shelf plant', 'Plant green', 380, 1680, 2160, 110)
    b.cyl('Shelf pot', 'Pot white', 270, 1680, 2330, 70, 120)
    b.sphere('Shelf plant', 'Plant green', 270, 1680, 2510, 120)
  end

  def self.ceiling_features(b)
    b.tag('Ceiling features')
    # Curved wooden bulkhead over the peninsula, rounded on its right end.
    bz0 = 2600
    # Glowing LED band at the bottom edge of the bulkhead (slightly proud of it).
    b.prism('Ceiling bulkhead', 'Warm oak', rounded_rect(0, 500, 2050, 1950, 500), bz0, H)
    b.prism('Bulkhead LED band', 'LED warm', rounded_rect(0, 488, 2062, 1962, 512), bz0 + 10, bz0 + 30)

    b.tag('Lighting')
    [650, 1050, 1450].each_with_index do |x, i|
      y = 1100
      cz = 1850
      b.cyl("Pendant #{i + 1} cord", 'Shelf dark', x, y, cz + 160, 4, bz0 - (cz + 160))
      b.cyl("Pendant #{i + 1} cap", 'Shelf dark', x, y, cz + 115, 30, 45)
      b.sphere("Pendant #{i + 1} globe", 'Pendant amber glass', x, y, cz, 130)
      b.sphere("Pendant #{i + 1} bulb", 'Bulb glow', x, y, cz, 35)
    end
    [[2600, 700], [3400, 900], [2300, 1800], [3300, 1900], [2400, 2800], [3400, 2900],
     [1300, 3200], [2400, 3500], [3400, 3600]].each_with_index do |(x, y), i|
      b.cyl("Downlight #{i + 1}", 'Bulb glow', x, y, H - 6, 50, 6)
    end
  end

  def self.decor(b)
    b.tag('Decor')
    # Peninsula: fruit bowl with oranges, glass vase with plant, two glasses.
    b.cyl('Fruit bowl', 'Ceramic cream', 1450, 1000, 920, 160, 60)
    [[1400, 960], [1500, 960], [1450, 1060], [1380, 1050], [1520, 1050]].each do |x, y|
      b.sphere('Orange', 'Orange', x, y, 1000, 42)
    end
    b.cyl('Glass vase', 'Glass', 330, 1050, 920, 70, 260)
    b.sphere('Vase plant', 'Plant green', 330, 1050, 1300, 170)
    b.cyl('Drinking glass', 'Glass', 520, 870, 920, 35, 120)
    b.cyl('Drinking glass', 'Glass', 600, 880, 920, 35, 120)

    # Back counter: pot on the hob, bottles, boards, small plants.
    b.cyl('Cooking pot', 'Stainless', 500, D - 380, 906, 120, 140)
    [1350, 1430, 1500].each_with_index do |x, i|
      b.cyl("Bottle #{i + 1}", 'Black gloss', x, D - 120, 900, 35, 260 - i * 30)
    end
    b.box('Chopping board', 'Chopping board', 1600, D - 40, 900, 1880, D - 20, 1280)
    b.box('Chopping board', 'Chopping board', 3720, D - 40, 900, 3880, D - 20, 1230)
    b.cyl('Counter pot', 'Pot white', 1250, D - 150, 900, 60, 100)
    b.sphere('Counter plant', 'Plant green', 1250, D - 150, 1060, 90)
    b.cyl('Counter pot', 'Pot white', 3100, D - 150, 900, 50, 90)
    b.sphere('Counter plant', 'Plant green', 3100, D - 150, 1040, 80)

    # Floor plant by the door, rug in front of the back run.
    b.cyl('Floor planter', 'Pot white', 3730, 3350, 0, 160, 420)
    b.cyl('Floor plant stem', 'Plant green', 3730, 3350, 420, 15, 450)
    b.sphere('Floor plant foliage', 'Plant green', 3730, 3350, 1050, 260)
    b.box('Rug', 'Rug', 1500, 2950, 0, 2900, 3450, 10)
  end

  # Plan outline of a rectangle whose two right-hand corners are rounded.
  def self.rounded_rect(x0, y0, x1, y1, r, segs = 8)
    pts = [[x0, y0]]
    arc = lambda do |cx, cy, a0, a1|
      (0..segs).map do |k|
        a = a0 + (a1 - a0) * k / segs.to_f
        [cx + r * Math.cos(a), cy + r * Math.sin(a)]
      end
    end
    pts += arc.call(x1 - r, y0 + r, -Math::PI / 2, 0)
    pts += arc.call(x1 - r, y1 - r, 0, Math::PI / 2)
    pts << [x0, y1]
    pts
  end

  # Camera matching the photo.
  CAMERA = { eye: [2350, -900, 1500], target: [2050, D, 1400], fov: 62 }.freeze

  # Creates SketchUp groups, tags and materials for the design primitives.
  class SketchUpBuilder
    def initialize(model, entities)
      @model = model
      @ents = entities
      @layer = nil
    end

    def tag(name)
      @layer = @model.layers[name] || @model.layers.add(name)
    end

    def box(name, mat, x0, y0, z0, x1, y1, z1)
      return if x1 <= x0 || y1 <= y0 || z1 <= z0

      g = group(name, mat)
      extrude(g, [[x0, y0], [x1, y0], [x1, y1], [x0, y1]], z0, z1 - z0)
      g
    end

    def prism(name, mat, pts, z0, z1)
      extrude(group(name, mat), pts, z0, z1 - z0)
    end

    def cyl(name, mat, cx, cy, z0, r, h, segs = 24)
      g = group(name, mat)
      circle = g.entities.add_circle(Geom::Point3d.new(cx.mm, cy.mm, z0.mm), Z_AXIS, r.mm, segs)
      face = g.entities.add_face(circle)
      face.reverse! if face.normal.z < 0
      face.pushpull(h.mm)
      g
    end

    def sphere(name, mat, cx, cy, cz, r, segs = 24)
      g = group(name, mat)
      c = Geom::Point3d.new(cx.mm, cy.mm, cz.mm)
      path = g.entities.add_circle(c, Z_AXIS, r.mm, segs)
      face = g.entities.add_face(g.entities.add_circle(c, X_AXIS, r.mm, segs))
      face.followme(path)
      g.entities.erase_entities(path.select(&:valid?))
      g
    end

    def floor(name, mat, x0, y0, x1, y1, tile)
      g = box(name, mat, x0, y0, -20, x1, y1, 0)
      # Tile joints drawn as edges on the top face so the floor shows its grid.
      (x0 + tile).step(x1 - 1, tile) { |x| g.entities.add_line([x.mm, y0.mm, 0], [x.mm, y1.mm, 0]) }
      (y0 + tile).step(y1 - 1, tile) { |y| g.entities.add_line([x0.mm, y.mm, 0], [x1.mm, y.mm, 0]) }
      g
    end

    private

    def group(name, mat)
      g = @ents.add_group
      g.name = name
      g.layer = @layer if @layer
      g.material = material(mat)
      g
    end

    def material(name)
      @model.materials[name] || begin
        r, gr, bl, a = MATERIALS.fetch(name)
        m = @model.materials.add(name)
        m.color = Sketchup::Color.new(r, gr, bl)
        m.alpha = a if a
        m
      end
    end

    def extrude(g, pts, z0, h)
      face = g.entities.add_face(pts.map { |x, y| Geom::Point3d.new(x.mm, y.mm, z0.mm) })
      face.reverse! if face.normal.z < 0
      face.pushpull(h.mm)
      g
    end
  end

  def self.build(save_path = File.expand_path('modular_kitchen.skp', File.dirname(__FILE__)))
    model = Sketchup.active_model
    model.start_operation('Build modular kitchen', true)
    begin
      model.options['UnitsOptions']['LengthUnit'] = 2 # millimetres
      root = model.active_entities.add_group
      root.name = 'Modular Kitchen'
      design(SketchUpBuilder.new(model, root.entities))
      model.commit_operation
    rescue StandardError
      model.abort_operation
      raise
    end

    eye, target = CAMERA.values_at(:eye, :target).map { |p| Geom::Point3d.new(*p.map(&:mm)) }
    model.active_view.camera = Sketchup::Camera.new(eye, target, Z_AXIS, true, CAMERA[:fov])
    model.pages.add('Reference view')
    saved = model.save(save_path)
    puts(saved ? "Modular kitchen saved to #{save_path}" : 'Model built; save it with File > Save As.')
    root
  end
end

ModularKitchen.build if defined?(Sketchup::Model)
