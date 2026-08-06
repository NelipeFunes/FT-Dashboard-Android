plugins {
    // Sem plugin de Kotlin: a AGP 9 compila Kotlin sozinha. O plugin de
    // compilador do Compose continua sendo aplicado normalmente.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "br.dev.ftdash"
    // 36 é o mínimo que a AGP 9 aceita.
    compileSdk = 37

    defaultConfig {
        applicationId = "br.dev.ftdash"
        minSdk = 26
        // Explícito de propósito: a AGP 9 passou a herdar o targetSdk do
        // compileSdk quando não declarado. 34 porque multimídia de carro roda
        // Android 9-11 e o app é sideload, não Play Store — subir o targetSdk
        // só traria restrições novas sem nenhum ganho aqui.
        targetSdk = 34
        versionCode = 3
        versionName = "0.9.1"
    }

    signingConfigs {
        // Assinado com a chave de debug de propósito.
        //
        // O app é sideload numa multimídia, nunca vai para a Play Store, e uma
        // chave própria só traria um segredo a mais para guardar e perder. O
        // que importa aqui é o APK ser instalável e o build ser release — o
        // `debuggable = false` é o que tira a penalidade de desempenho que
        // atrapalharia um painel redesenhando a 17 Hz.
        create("sideload") {
            storeFile = File(System.getProperty("user.home"), ".android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // R8 desligado: o ganho seria de tamanho, e o risco é quebrar em
            // tempo de execução a serialização do perfil de marchas — coisa
            // que só apareceria dentro do carro. Não vale a troca no primeiro
            // teste de campo.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("sideload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

// O jvmTarget do Kotlin vem de compileOptions.targetCompatibility acima —
// com o Kotlin embutido da AGP não é preciso declarar de novo.

dependencies {
    implementation(project(":core:protocol"))
    implementation(project(":core:data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
