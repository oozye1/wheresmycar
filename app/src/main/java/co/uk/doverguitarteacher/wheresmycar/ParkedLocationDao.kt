package co.uk.doverguitarteacher.wheresmycar;

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ParkedLocationDao {
    @Upsert
    suspend fun upsertParkedLocation(location: ParkedLocation)

    @Query("SELECT * FROM parked_location_table WHERE id = 1")
    fun getParkedLocation(): Flow<ParkedLocation?>
}
