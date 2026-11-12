// Gera os ícones do Android a partir de www/icone-512.png.
// Ícone adaptativo: a frente é o ícone inteiro (o capacitor-assets o recua 16,7% para a
// área visível); o fundo é o próprio ícone ampliado e desfocado, para as bordas seguirem
// a cor dele. Usa o sharp, que já vem com o @capacitor/assets.
import { createRequire } from 'node:module';
import { mkdirSync } from 'node:fs';
import { execSync } from 'node:child_process';

const require = createRequire(import.meta.url);
const sharp = require(require.resolve('sharp', { paths: [require.resolve('@capacitor/assets')] }));

const origem = 'www/icone-512.png';
mkdirSync('assets', { recursive: true });
await sharp(origem).resize(1024, 1024).png().toFile('assets/icon-only.png');
await sharp(origem).resize(1024, 1024).png().toFile('assets/icon-foreground.png');
await sharp(origem).flatten({ background: '#ffffff' }).resize(1024, 1024, { fit: 'fill' }).blur(60).png().toFile('assets/icon-background.png');
execSync('npx capacitor-assets generate --android', { stdio: 'inherit' });
