// Copia o KaTeX para www/katex/ com as fontes woff2 embutidas no CSS (data:),
// para o HTMLTi renderizar LaTeX dentro dos apps mesmo offline e sem depender de CORS.
// Uso: npm run katex
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';

const require = createRequire(import.meta.url);
const dist = join(dirname(require.resolve('katex/package.json')), 'dist');
const versao = JSON.parse(readFileSync(join(dist, '..', 'package.json'), 'utf8')).version;
mkdirSync('www/katex', { recursive: true });
let css = readFileSync(join(dist, 'katex.min.css'), 'utf8');
// Só woff2 (todos os navegadores atuais leem), embutido
css = css.replace(/src:url\(fonts\/([^)]+?\.woff2)\) format\("woff2"\)(,url\([^)]+\) format\("[^"]+"\))*/g, (m, f) =>
  `src:url(data:font/woff2;base64,${readFileSync(join(dist, 'fonts', f)).toString('base64')}) format("woff2")`);
if (/url\(fonts\//.test(css)) throw new Error('Sobrou fonte sem embutir no CSS do KaTeX');
writeFileSync('www/katex/katex.css', `/* KaTeX ${versao} (MIT), fontes embutidas */\n` + css);
writeFileSync('www/katex/katex.min.js', readFileSync(join(dist, 'katex.min.js')));
writeFileSync('www/katex/auto-render.min.js', readFileSync(join(dist, 'contrib', 'auto-render.min.js')));
writeFileSync('www/katex/LICENSE', readFileSync(join(dist, '..', 'LICENSE')));
console.log('KaTeX', versao, 'copiado para www/katex/');
