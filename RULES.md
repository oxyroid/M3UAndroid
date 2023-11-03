# Rules

1. Do not import-on-demand (star-import).
2. All composable functions should be restartable and skippable.
3. Do not use List/Map/Set as parameters for composable functions. Instead, consider using lambdas
   like `() -> List/Map/Set` or immutable data structures.
4. If you want to change the visibility of the system bars, you can do so by calling
   `Helper#statusBarsVisibility` or `Helper#navigationBarsVisibility`.
5. If you want to create a new string resource, you can do so by creating it in the i18n module and
   then accessing it using `import <package>.i18n.R.string`.
6. If you wish to apply additional dependencies, consider using version catalogs.