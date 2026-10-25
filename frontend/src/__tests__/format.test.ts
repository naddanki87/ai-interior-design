import { agentLabel, duration, lines, svgDataUrl } from '../format';

describe('format helpers', () => {
  it('formats durations', () => {
    expect(duration(null)).toBe('–');
    expect(duration(250)).toBe('250 ms');
    expect(duration(1500)).toBe('1.5 s');
    expect(duration(125000)).toBe('2m 5s');
  });

  it('splits list input on commas and new lines', () => {
    expect(lines('sofa\n2 armchairs, TV unit\n\n')).toEqual(['sofa', '2 armchairs', 'TV unit']);
  });

  it('labels participants', () => {
    expect(agentLabel('USER', {})).toBe('You');
    expect(agentLabel('GEOMETRY', { GEOMETRY: 'Geometry' })).toBe('Geometry');
  });

  it('encodes SVG as an image data URL (never injected as markup)', () => {
    const url = svgDataUrl('<svg><script>alert(1)</script></svg>');
    expect(url.startsWith('data:image/svg+xml')).toBe(true);
    expect(url).not.toContain('<script>');
  });
});
