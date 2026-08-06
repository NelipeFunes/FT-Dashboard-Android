# kotlinx.serialization: os serializadores gerados são acessados por reflexão
# no companion, então não podem ser renomeados.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class br.dev.ftdash.gearing.** {
    *** Companion;
}
-keepclasseswithmembers class br.dev.ftdash.gearing.** {
    kotlinx.serialization.KSerializer serializer(...);
}
