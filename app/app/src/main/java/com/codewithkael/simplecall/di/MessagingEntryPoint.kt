package com.codewithkael.simplecall.di

import com.codewithkael.simplecall.data.MessagesRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt entry point so components that Hilt can't constructor-inject cleanly —
 * the manifest-registered SmsReceiver and the NotificationListenerService —
 * can still reach the singleton [MessagesRepository] via
 * EntryPointAccessors.fromApplication(context, MessagingEntryPoint::class.java).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface MessagingEntryPoint {
    fun messagesRepository(): MessagesRepository
}
