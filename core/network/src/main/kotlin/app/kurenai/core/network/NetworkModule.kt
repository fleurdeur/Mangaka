package app.kurenai.core.network

import app.kurenai.core.network.challenge.InteractiveChallengeManager
import app.kurenai.core.network.cookies.EncryptedCookieJar
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import android.content.Context
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideCookieJar(@ApplicationContext context: Context): EncryptedCookieJar = EncryptedCookieJar(context)

    @Provides
    @Singleton
    fun provideInteractiveChallengeManager(): InteractiveChallengeManager = InteractiveChallengeManager()

    @Provides
    @Singleton
    fun provideDiagnostics(): NetworkDiagnostics = NetworkDiagnostics()
}
