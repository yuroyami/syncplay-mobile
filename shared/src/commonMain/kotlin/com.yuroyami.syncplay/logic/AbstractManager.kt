package com.yuroyami.syncplay.logic

/**
 * Base class for all "manager" components in the Syncplay architecture.
 *
 * The goal is to avoid dumping every responsibility into [SyncplayViewmodel],
 * which would turn it into a giant "god object." Instead, each manager:
 * - Handles one specific domain (e.g., lifecycle, OSD, snackbars)
 * - Has full access to [SyncplayViewmodel] for coordination
 * - Remains lightweight, modular, and testable
 */
abstract class AbstractManager(
    val viewmodel: SyncplayViewmodel
)
