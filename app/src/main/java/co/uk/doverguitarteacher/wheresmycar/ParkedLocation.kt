package co.uk.doverguitarteacher.wheresmycar;



import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "parked_location_table")
data class ParkedLocation(
    @PrimaryKey val id: Int = 1,
    val latitude: Double,
    val longitude: Double
)
