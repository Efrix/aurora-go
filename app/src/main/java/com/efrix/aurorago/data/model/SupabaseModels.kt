package com.efrix.aurorago.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.UUID

object UUIDSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: UUID) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
}

@Serializable
data class Perfil(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val nombre_usuario: String = "",
    val edad: Int? = null,
    val fecha_creacion: String = "",
    val updated_at: String = "",
    val miedos: List<MiedoData> = emptyList()
)

@Serializable
data class MiedoData(
    val tipo: String = "",
    val intensidad: Int = 0,
    val contexto: String = ""
)

@Serializable
data class ProgresoMiedo(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @Serializable(with = UUIDSerializer::class)
    val usuario_id: UUID,
    val miedo_id: String = "",
    val hp_restante: Int = 100,
    val updated_at: String = ""
)

@Serializable
data class Checkin(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @Serializable(with = UUIDSerializer::class)
    val usuario_id: UUID,
    val fecha: String = "",
    val respuestas: Map<String, Int> = emptyMap(),
    val nota: String = "",
    val created_at: String = ""
)