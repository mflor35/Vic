package com.vicmod.state;

import net.minecraft.nbt.CompoundTag;

public class VicTask {
    private String id;
    private String title;
    private String description;
    private String targetType;
    private int currentStep;
    private int totalSteps;
    private int currentProgress;
    private int targetProgress;
    private boolean completed;
    private int dayStarted;

    public VicTask(String id, String title, String description, String targetType, int currentStep, int totalSteps, int currentProgress, int targetProgress, boolean completed, int dayStarted) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.targetType = targetType;
        this.currentStep = currentStep;
        this.totalSteps = totalSteps;
        this.currentProgress = currentProgress;
        this.targetProgress = targetProgress;
        this.completed = completed;
        this.dayStarted = dayStarted;
    }

    public static VicTask createDefaultShelterTask(int day) {
        return new VicTask(
                "build_shelter",
                "Construct Safe Shelter",
                "Gather wood and iron, then erect a roofed fortification before nightfall.",
                "BUILD_SHELTER",
                1,
                3,
                0,
                16,
                false,
                day
        );
    }

    public static VicTask createGatherIronTask(int day) {
        return new VicTask(
                "gather_iron",
                "Reinforce Defense Grid",
                "Mine and smelt 12 iron ingots to reinforce the perimeter.",
                "GATHER_IRON",
                1,
                2,
                0,
                12,
                false,
                day
        );
    }

    public static VicTask createStockpileFoodTask(int day) {
        return new VicTask(
                "stockpile_food",
                "Ration Preservation",
                "Collect 10 cooked food items to reduce biological vulnerability.",
                "FIND_FOOD",
                1,
                1,
                0,
                10,
                false,
                day
        );
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getTargetType() {
        return targetType;
    }

    public int getCurrentStep() {
        return currentStep;
    }

    public int getTotalSteps() {
        return totalSteps;
    }

    public int getCurrentProgress() {
        return currentProgress;
    }

    public int getTargetProgress() {
        return targetProgress;
    }

    public boolean isCompleted() {
        return completed;
    }

    public int getDayStarted() {
        return dayStarted;
    }

    public void advanceProgress(int delta) {
        this.currentProgress = Math.min(this.targetProgress, this.currentProgress + delta);
        if (this.currentProgress >= this.targetProgress) {
            if (this.currentStep < this.totalSteps) {
                this.currentStep++;
                this.currentProgress = 0;
            } else {
                this.completed = true;
            }
        }
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public String formatStatus() {
        if (completed) {
            return "[" + title + ": COMPLETED]";
        }
        return "[" + title + " (Step " + currentStep + "/" + totalSteps + "): " + currentProgress + "/" + targetProgress + " progress]";
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id != null ? id : "");
        tag.putString("title", title != null ? title : "");
        tag.putString("description", description != null ? description : "");
        tag.putString("targetType", targetType != null ? targetType : "");
        tag.putInt("currentStep", currentStep);
        tag.putInt("totalSteps", totalSteps);
        tag.putInt("currentProgress", currentProgress);
        tag.putInt("targetProgress", targetProgress);
        tag.putBoolean("completed", completed);
        tag.putInt("dayStarted", dayStarted);
        return tag;
    }

    public static VicTask fromNbt(CompoundTag tag) {
        return new VicTask(
                tag.getStringOr("id", "task"),
                tag.getStringOr("title", "Active Task"),
                tag.getStringOr("description", ""),
                tag.getStringOr("targetType", "CUSTOM"),
                tag.getIntOr("currentStep", 1),
                tag.getIntOr("totalSteps", 1),
                tag.getIntOr("currentProgress", 0),
                tag.getIntOr("targetProgress", 1),
                tag.getBooleanOr("completed", false),
                tag.getIntOr("dayStarted", 1)
        );
    }
}
