# Copyright 2015 Google Inc.  All rights reserved.
# Author: cgruber@google.com (Christian Gruber)
load("@rules_java//java:defs.bzl", "java_library")
load("//:test_defs.bzl", "guice_test_suites")

package(default_testonly = 1)

java_library(
    name = "tests",
    srcs = glob(["**/*.java"]),
    plugins = [
    ],
    deps = [
        "//core/src/com/google/inject",
        "//extensions/dagger-adapter/src/com/google/inject/daggeradapter",
        "//third_party/java/auto:value",
        "//third_party/java/dagger",
        "//third_party/java/dagger:producers",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/jakarta_inject",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)

guice_test_suites(
    name = "gen_tests",
    sizes = ["small"],
    deps = [":tests"],
)

[guice_test_suites(
    name = "gen_tests_%s" % include_stack_trace_option,
    args = [
        "--guice_include_stack_traces=%s" % include_stack_trace_option,
    ],
    sizes = [
        "small",
    ],
    suffix = "_stack_trace_%s" % include_stack_trace_option,
    deps = [":tests"],
) for include_stack_trace_option in [
    "OFF",
]]
