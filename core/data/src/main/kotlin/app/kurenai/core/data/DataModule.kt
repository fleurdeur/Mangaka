package app.kurenai.core.data

import android.content.Context
import androidx.room.Room
import app.kurenai.core.common.AppEventBus
import app.kurenai.core.database.KurenaiDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KurenaiDatabase = KurenaiDatabase.build(context)

    @Provides fun mangaDao(db: KurenaiDatabase) = db.mangaDao()
    @Provides fun chapterDao(db: KurenaiDatabase) = db.chapterDao()
    @Provides fun historyDao(db: KurenaiDatabase) = db.historyDao()
    @Provides fun categoryDao(db: KurenaiDatabase) = db.categoryDao()
    @Provides fun downloadDao(db: KurenaiDatabase) = db.downloadDao()
    @Provides fun updateDao(db: KurenaiDatabase) = db.updateDao()
    @Provides fun trackerDao(db: KurenaiDatabase) = db.trackerDao()
    @Provides fun statsDao(db: KurenaiDatabase) = db.statsDao()
    @Provides fun sourceHealthDao(db: KurenaiDatabase) = db.sourceHealthDao()
    @Provides fun searchHistoryDao(db: KurenaiDatabase) = db.searchHistoryDao()
    @Provides fun aiCacheDao(db: KurenaiDatabase) = db.aiCacheDao()

    @Provides
    @Singleton
    fun provideEventBus(): AppEventBus = AppEventBus()
}
