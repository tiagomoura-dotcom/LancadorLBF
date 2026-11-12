// Gera os ícones do Android a partir de www/icone-512.png.
// Ícone adaptativo: a frente é o ícone inteiro (o capacitor-assets o recua 16,7% para a
// área visível) e o fundo é uma cor lisa tirada da borda do próprio ícone, para os cantos
// arredondados transparentes sumirem. Usa o sharp, que já vem com o @capacitor/assets.
import { createRequire } from 'node:module';
import { mkdirSync } from 'node:fs';
import { execSync } from 'node:child_process';

const require = createRequire(import.meta.url);
const sharp = require(require.resolve('sharp', { paths: [require.resolve('@capacitor/assets')] }));

const origem = 'www/icone-512.png';
const { width, height } = await sharp(origem).metadata();
const px = await sharp(origem)
  .extract({ left: Math.round(width * 0.05), top: Math.round(height / 2), width: 1, height: 1 })
  .flatten({ background: '#ffffff' })
  .raw()
  .toBuffer();
const cor = { r: px[0], g: px[1], b: px[2] };

mkdirSync('assets', { recursive: true });
await sharp(origem).resize(1024, 1024).png().toFile('assets/icon-only.png');
await sharp(origem).resize(1024, 1024).png().toFile('assets/icon-foreground.png');
await sharp({ create: { width: 1024, height: 1024, channels: 3, background: cor } }).png().toFile('assets/icon-background.png');
execSync('npx capacitor-assets generate --android', { stdio: 'inherit' });
