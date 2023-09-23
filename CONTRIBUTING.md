# How to become a contributor and submit your own code

- Fork the repository
- Open your IDEA (or Android Studio) to change your settings:
    - Settings -> Editor -> Code Style -> Kotlin(Choose Project Schema) -> Imports ->
    - Top-Level Symbols: Use single name import
    - Java Statics and Enum Members: Use single name import
    - Packages to Use Import with "*": none
- Create new branch from master or checkout **other** exist branches.
    - **Branch Naming Rules**:
    - > Release Package is allowed in MASTER branch only 
      > and other contributors cannot change MASTER branch without PRs.
        - screen feature branch: feature_{module-name}
        - bug fix branch: fix_{keyword}
        - migrate platform branch: {target-platform}_migrate
        - other branch: build_{keyword}
    - **Commit Rules**:
      > format requirement: "lower-case", "space after the colon", "end of period"
      > {catalog}: {description}.
        - feat: new screen feature.
        - fix: bug fix.
        - docs: markdown files or comments update.
        - build: gradle files or project structure update.
        - upgrade(master only): release new version.
        - style: code style or naming update.
        - etc.
- *CODING* in your branch
- Make sure no warnings or errors existed then push your code.
- Make sure completed staged code then you can make a pull request to MASTER branch.