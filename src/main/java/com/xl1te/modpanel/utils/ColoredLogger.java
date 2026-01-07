package com.xl1te.modpanel.utils;

import java.util.logging.Logger;

public class ColoredLogger {
    private Logger logger;

    public ColoredLogger(Logger logger) {
        this.logger = logger;
    }

    public void info(String message) {
        logger.info("\u001B[32m" + message + "\u001B[0m");
    }

    public void warning(String message) {
        logger.warning("\u001B[33m" + message + "\u001B[0m");
    }

    public void severe(String message) {
        logger.severe("\u001B[31m" + message + "\u001B[0m");
    }
}