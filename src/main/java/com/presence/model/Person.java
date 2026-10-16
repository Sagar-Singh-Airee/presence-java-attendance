/**
 * Purpose: Represents one registered person.
 * Why it is used: It carries a person's ID, name, and registration details between parts of the app.
 */
package com.presence.model;

import java.time.LocalDateTime;

public record Person(long id, String name, LocalDateTime createdAt, String sampleImagePath) { }
