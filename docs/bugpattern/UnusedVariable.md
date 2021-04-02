The presence of an unused variable may indicate a bug. This check highlights
_private_ fields and parameters which are unused and can be safely removed
without considering the impact on other source files.

## Suppression

False positives on fields and parameters can be suppressed by prefixing the
variable name with `unused`, e.g.:

```java
private static void authenticate(User user, Application unusedApplication) {
  checkState(user.isAuthenticated());
}
```

All false positives can be suppressed by annotating the variable with
`@SuppressWarnings("unused")`.
