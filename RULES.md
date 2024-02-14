# Rules

1. Never use Java, use Kotlin always.
2. Do not import-on-demand (star-import).
3. All composable functions without return types should be restartable and skippable.
4. Do not use List/Map/Set and other unstable collections as parameters for composable functions.
   Instead, consider using stable wrapper
   or immutable data structures with immutable/stable elements.
5. If you want to change the visibility of the system bars, you can do so by calling
   `Helper#statusBarsVisibility` or `Helper#navigationBarsVisibility`.
6. If you want to create a new string resource, you can do so by creating it in the i18n module and
   then accessing it using `import <package>.i18n.R.string`.
7. If you wish to apply additional dependencies, consider using version catalogs.
8. Never use AndroidViewModel, use context in UI layer only.
9. Never use view-based XML, you can use view in AndroidView composable only.
10. Never use Painter to inflate drawable resources, use `ImageVector.vectorResource` instead.
11. If you wanna to add some libraries, please make sure they are located in MavenCentral, google or
    jitpack repository. And jar library is not allowed as well.
12. Due to compatibility needs, for data table `playlists` and `streams`,
    please do not change the existing column names(referring to the real field names mapped to the
    database, that is, the name field defined in ColumnInfo),
    and remember the new fields must have default value (it needs to be defined in the
    defaultValue field in both the data class and ColumnInfo).
