import Reveal from 'reveal.js';
import RevealMarkdown from 'reveal.js/plugin/markdown/markdown.esm';
import 'reveal.js/dist/reveal.css'
import 'reveal.js/dist/theme/sky.css'
import mermaid from "mermaid";

mermaid.initialize({startOnLoad: true})

let deck = new Reveal({
    plugins: [ RevealMarkdown ],
    progress: true
})
deck.initialize({
    hash: true
});