# How AI is used in Aperture
**Aperture is developed with AI as a development tool, but not as an autonomous author like many projects.**

There's no point in denying it, and it seems weird when people do. AI is used extensively to accelerate implementation.

I'll often provide a detailed specification, relevant snippets, existing architectural context, and constraints, and then request that an AI produce an initial implementation of a feature or change of some sort, or even sometimes a complete rework.

I then review the resulting code, test it, and modify or rewrite it as necessary.

The initial project scaffold was generated with Gemini's app builder tool in Android Studio, and AI has still been used from then on to implement pretty large portions of individual features. Autocomplete is also used regularly for the smaller changes and some boilerplate.

**AI-generated code is never treated as the final design. I am still responsible for the architecture, behaviour, testing, and visual design of Aperture.**

Speaking of the visual design, the recent Material 3 Expressive work often involves a very large amount of manual adaptation. M3E's new tokens sometimes need to be reinterpreted for Compose for TV, like for focus handling, and motion and interaction behaviour is manually tested and refined for a TV viewing distance and remote-control interaction model.

AI is super useful for getting a feature to a strong functional starting point, too. I then often need to make it fit Aperture's design system, including its semantic tokens, motion system, focus behaviour, and interaction patterns that are specific to TV too. Oftentimes an AI-generated implementation does not fit those systems, so I rewrite it instead of preserving it because "it works".

I reckon that around 20% of the code is directly written by me, although this does not represent the proportion of human contribution to the project. Most AI-generated code is reviewed by me, and I make the final decisions about its architecture, behaviour, and integration.

AI is simply one of the tools I use to build Aperture faster and explore implementations I might not otherwise have considered.
