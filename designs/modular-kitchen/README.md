# Modular kitchen: SketchUp model of the reference photo

`reference.jpg` is the design. `modular_kitchen.rb` rebuilds it in SketchUp, keeping the layout, units, finishes and lighting shown in the photo. `preview.png` is a quick render of the same geometry from the photo's camera angle, so you can compare the two before opening SketchUp.

## Build it in your SketchUp

1. Open SketchUp and start a new, empty model (any millimetre template).
2. Go to **Window → Ruby Console** (in SketchUp 2024+ it may be under **Extensions → Developer → Ruby Console**).
3. Run (with your own path):
   ```ruby
   load 'C:/Users/<you>/Downloads/modular_kitchen.rb'
   ```
4. The kitchen is built as one group, **Modular Kitchen**, with a **Reference view** scene that matches the photo. It is saved as `modular_kitchen.skp` next to the script. To undo the whole build, use **Edit → Undo** once.

Every part is a named group on its own tag, so you can hide whole sections. For example, hide the **Ceiling** tag to look down into the room.
Tags: Structure, Ceiling, Window, Base cabinets, Wall cabinets, Countertops, Appliances, Door, Tall unit, Peninsula, Display shelf, Ceiling features, Lighting, Decor.

## What is modelled (as in the photo)

- **Back run:** glossy taupe base units (drawers and doors), gas hob with a pot, black chimney hood, stainless dishwasher, sink and faucet, light marble counter, aqua-marble backsplash, plinth LED.
- **Wall units:** dark open shelving with LED around the hood, plus glossy taupe 2-door × 2-tier cabinets up to the ceiling on both sides of the window, with under-cabinet LED.
- **Window:** grey frame with a centre mullion, two half-lowered beige roman blinds and a marble sill.
- **Right wall:** walnut door, black French-door fridge with a black loft above, and a tall unit with wicker baskets, oven, microwave and open top.
- **Peninsula:** fluted oak-slat front, aqua-marble top and end panel, LED at the top and plinth, a fruit bowl with oranges, a glass vase with a plant, and glasses.
- **Display shelf:** oak open shelves with LED, vases and plants.
- **Ceiling:** curved oak bulkhead with an LED band, three amber glass globe pendants, and recessed downlights.
- **Floor:** glossy cream marble with an 800 mm tile grid, a rug, and a floor plant by the door.

## Sizes

The photo has no measurements, so standard modular-kitchen sizes were used, matched to the photo's proportions:

| | mm |
|---|---|
| Room (width × depth × height) | 4000 × 4800 × 2850 |
| Base units | 600 deep, counter top at 900 |
| Wall units | 350 deep, from 1650 to the ceiling |
| Window | 1200 × 1600, sill at 1000 |
| Door | 850 × 2100 |
| Peninsula | 1950 × 860, top at 920 |
| Fridge | 900 wide, 650 deep |

The main sizes are constants at the top of `modular_kitchen.rb` (`W`, `D`, `H`, `COUNTER_TOP`, `UPPER_BOTTOM`, window and door positions). Change them to your site measurements and run the script again in a new model.

SketchUp has no Linux version, so this script was checked against a stub of the SketchUp Ruby API and rendered with three.js. It has not been run in real SketchUp yet. If the Ruby Console shows an error, copy it back and it can be fixed.
