package app.kurenai.core.parsers

import app.kurenai.core.source.SourceRegistry
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ParsersModule {
    @Binds
    abstract fun bindSourceRegistry(registry: KotatsuSourceRegistry): SourceRegistry
}
