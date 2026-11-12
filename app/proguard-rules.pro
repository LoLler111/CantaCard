# Nessuna regola specifica: l'app non usa reflection né serializzazione basata su reflection
# (il JSON è scritto e letto a mano con org.json del framework).

# Rimuove i log di debug/verbose/info dalla release.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
