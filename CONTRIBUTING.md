# How to Become a Contributor and Submit Your Own Code

If you want to contribute to this repository, follow these steps:

1. **Fork the Repository**
   
   Unselect `copy master branch only` if you want to base other branches.

2. **Configure Your IDE (IDEA or Android Studio)**
    - Navigate to `Settings` -> `Editor` -> `Code Style` -> `Kotlin` (Choose Project Schema) -> `Imports`.
    - Set the following options:
        - Top-Level Symbols: Use single name import.
        - Java Statics and Enum Members: Use single name import.
        - Packages to Use Import with "*": none.

3. **Create a New Branch**

   Create a new branch from the `master` branch or checkout existing branches.

4. **Branch Naming Rules**

   If you want to code in new branch, use the following naming conventions:
   - Screen feature branch: `feature_{module-name}`
   - Bug fix branch: `fix_{keyword}`
   - Migrate platform branch: `{target-platform}_migrate`
   - Other branch: `build_{keyword}`

5. **Coding in Your Branch**
   
   Start coding in your branch and make the necessary changes.

6. **Check for Warnings and Errors**

   Ensure that there are no warnings or errors in your code.

7. **Commit Rules**

   Follow this format for your commits: "lower-case", "space after the colon", "end with a period".
   Use this format: `{catalog}: {description}.`.

   Examples:
   - `feat: new screen feature.`
   - `fix: bug fix.`
   - `docs: markdown files or comments update.`
   - `build: gradle files or project structure update.`
   - `upgrade (master only): release new version.`
   - `style: code style or naming update.`
   - etc.

8. **Push Your Code**

   Once your code is ready, push it to your branch.

9. **Make a Pull Request**
   
   If your staged code is complete and error-free, you can create a pull request to the `MASTER` branch.

Following these guidelines will help streamline the contribution process. Thank you for your contribution!