CREATE TABLE IF NOT EXISTS `lesson_notes` (
    `lessonId` TEXT NOT NULL,
    `note` TEXT NOT NULL,
    `updatedAt` INTEGER NOT NULL,
    PRIMARY KEY (`lessonId`)
);
