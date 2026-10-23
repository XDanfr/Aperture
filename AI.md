# How AI is used in Aperture
**Aperture is developed with AI as a development tool, but not as an autonomous author like many projects.**

There's no point in denying it, and it seems weird when people do. AI is used a fair bit to accelerate implementation.

I'll often provide a detailed specification, relevant snippets, existing context, and constraints, and then request that an AI produce an initial implementation of a feature or change of some sort, or even sometimes a complete rework.

I then review the resulting code, test it, and modify or rewrite it as necessary.

The initial project scaffold was generated with Gemini's app builder tool in Android Studio, and AI like Codex has still been used from then on to implement fairly large portions of individual features. Autocomplete is also used regularly for the smaller changes and some boilerplate, and that's where the big usage really is.

**AI-generated code is never, ever treated as the final design. I am still responsible for the architecture, behaviour, testing, and visual design of Aperture.**

Speaking of the visual design, the recent Material 3 Expressive work often involves a very large amount of manual adaptation. M3E's new tokens sometimes need to be reinterpreted for Compose for TV, like for focus handling, and motion and interaction behaviour is always manually tested and refined for a TV viewing distance and remote-control interaction model.

AI is super useful for getting a feature to a strong functional starting point, too. I then often need to make it fit Aperture's design system, including its semantic tokens, motion system, focus behaviour, and interaction patterns that are specific to TV too. Oftentimes an AI-generated implementation doesn't fit those systems, so I rewrite it instead of preserving it because "it works" because I want an app that also feels good to use.

I reckon that around 40% of the code is directly written by me, although this doesn't really represent the proportion of human contribution to the project. All AI-generated code is reviewed by me, and I make the final decisions about its architecture, behaviour, and integration, plus ensure that things be steered by myself when necessary. It's a tool to do things quicker and more extensively at the end of the day.

So yeah! AI is just one of the tools I use to build Aperture faster and explore implementations I mightn't have otherwise considered.
