const fs = require('fs');
const content = fs.readFileSync('F:/theFaleOne-Frontend/FALE-healthcare-ui/src/routes/onboarding.tsx', 'utf8');

const setMatches = [...content.matchAll(/set\((['"])(.*?)\1\)/g)].map(m => m[2]);
const strMatches = [...content.matchAll(/str\((['"])(.*?)\1\)/g)].map(m => m[2]);
const arrMatches = [...content.matchAll(/arr\((['"])(.*?)\1\)/g)].map(m => m[2]);
const pMatches = [...content.matchAll(/p\[(['"])(.*?)\1\]/g)].map(m => m[2]);

console.log('set keys:', JSON.stringify([...new Set(setMatches)].sort()));
console.log('str keys:', JSON.stringify([...new Set(strMatches)].sort()));
console.log('arr keys:', JSON.stringify([...new Set(arrMatches)].sort()));
console.log('p[key] keys:', JSON.stringify([...new Set(pMatches)].sort()));

const allKeys = [...new Set([...setMatches, ...strMatches, ...arrMatches, ...pMatches])].sort();
console.log('\nAll unique field keys in onboarding.tsx:\n', allKeys);

