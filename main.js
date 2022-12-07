import Reveal from 'reveal.js'
import 'reveal.js/dist/reveal.css'
import 'reveal.js/dist/theme/sky.css'
import 'reveal.js/plugin/highlight/monokai.css'
import RevealHighlight from 'reveal.js/plugin/highlight/highlight.js'
import mermaid from "mermaid"

mermaid.initialize({startOnLoad: true})

let deck = new Reveal({
    plugins: [ RevealHighlight ],
    progress: true,
    width: "100%",
    height: "100%"
})
deck.initialize({
    hash: true
});