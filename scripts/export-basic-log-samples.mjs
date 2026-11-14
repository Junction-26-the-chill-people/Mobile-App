// Export only generated story people and three basic measurements. Never bundle the research cohort.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';

const input = process.argv[2];
if (!input) throw new Error('Usage: node scripts/export-basic-log-samples.mjs path/to/relapse-demo/data/demo-data.json');
const bytes = fs.readFileSync(input);
const source = JSON.parse(bytes.toString('utf8'));
const releaseFile = path.resolve(path.dirname(input), '../release.json');
const release = JSON.parse(fs.readFileSync(releaseFile, 'utf8')).release;
const number = value => typeof value === 'number' && Number.isFinite(value) ? value : null;
const profiles = source.participants.filter(p => p.synthetic === true && p.datasetId === 'synthetic-daily' && /^sim-\d{2}$/.test(p.id)).map(p => ({
  id: p.id, name: p.name.split(/\s[·-]\s/)[0], synthetic: true,
  selectedDate: p.records[p.defaultIndex ?? 100]?.date || p.records.at(-1).date,
  records: p.records.map(r => {
    if (r.synthetic !== true || r.participantId !== p.id) throw new Error('A sample record has invalid synthetic provenance.');
    return { date: r.date, mood: number(r.values?.mood), sleepHours: number(r.values?.sleep_hours), steps: number(r.values?.steps), missing: r.missingPeriod === true };
  }),
}));
if (profiles.length !== 11) throw new Error('Expected the eleven documented synthetic story profiles.');
const output = {
  schemaVersion: 1, synthetic: true,
  source: 'WITHIN demo stories', sourceRepository: 'Junction-26-the-chill-people/Data-Collection',
  sourcePath: 'relapse-demo/data/demo-data.json', sourceRelease: release,
  sourceSha256: createHash('sha256').update(bytes).digest('hex'),
  moodScale: { minimum: 1, maximum: 5, unit: '/5' },
  notice: 'Fictional demo profiles. Basic logs only; no analysis, research records or location data.',
  profiles,
};
const target = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../app/src/main/assets/sola/basic-log-samples.json');
fs.writeFileSync(target, JSON.stringify(output) + '\n');
console.log(`Exported ${profiles.length} synthetic profiles, ${profiles.reduce((n,p)=>n+p.records.length,0)} basic day records; ${fs.statSync(target).size} bytes.`);
