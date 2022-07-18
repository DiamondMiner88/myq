package com.github.diamondminer88.myq.model

import com.github.diamondminer88.myq.UUIDSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
internal data class MyQAccountsResponse(
	val accounts: List<MyQAccount>,
)

@Serializable
public data class MyQAccount(
	val name: String,

	@Serializable(with = UUIDSerializer::class)
	val id: UUID,

	@Serializable(with = UUIDSerializer::class)
	@SerialName("created_by")
	val createdBy: UUID,

	@SerialName("max_users")
	val maxUsers: MyQAccountMaxUsers,
)

@Serializable
public data class MyQAccountMaxUsers(
	val guest: Int,
	val co_owner: Int,
)
