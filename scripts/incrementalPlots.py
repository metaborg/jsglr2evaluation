import csv
from os import makedirs, path, scandir, remove, environ

import matplotlib.pyplot as plt
import mpl_toolkits.mplot3d.art3d as art3d
import pdftools

DATA_DIR = environ["JSGLR2EVALUATION_DATA_DIR"]
FIGURES_DIR = environ["JSGLR2EVALUATION_FIGURES_DIR"]

COLORS = {
    "Batch": "rs",
    "Incremental": "g^",
    "IncrementalNoCache": "bv",
    "jsglr2-standard": "rs",
    "jsglr2-elkhound": "yo",
    "jsglr2-incremental": "bv",  # TODO fix color for incremental: with vs. without cache
}

MiB = 1024 * 1024


def read_csv(p):
    with open(p) as csv_file:
        lines = list(csv.reader(csv_file, delimiter=','))
        header = lines[0]
        return [{h: v for h, v in zip(header, line)} for line in lines[1:]]


def base_plot(plot_size, title, x_label, y_label, z_label="", subplot_kwargs=None):
    fig = plt.figure(figsize=plot_size[:2])
    ax = fig.add_subplot(**(subplot_kwargs if subplot_kwargs is not None else {}))

    ax.margins(*(0.1 / x for x in plot_size))

    ax.set_title(title, y=1.0 + 0.4 / float(plot_size[1]), va="bottom")
    ax.set_xlabel(x_label)
    ax.set_ylabel(y_label)
    if z_label:
        ax.set_zlabel(z_label)

    return fig, ax


def plot_memory_batch(rows, garbage):
    if garbage != "incl" and garbage != "excl":
        raise Exception("Invalid argument")

    memoryColumn = f"Memory ({garbage}. garbage)"
    memoryErrorColumn = f"Error {garbage}."

    plot_size = (8, 6)
    title = "Memory usage during parsing in batch mode" if garbage == "incl" else "Memory increase of cache after first parse"
    fig, ax = base_plot(plot_size, title, "File size (bytes)", "Memory (MiB)")

    parsers = list(set(row["Parser"] for row in rows))
    for parser in parsers:
        x, y, y_err = zip(*((int(row["Size"]), float(row[memoryColumn]) / MiB, float(row[memoryErrorColumn] or 0) / MiB)
                            for row in rows if row["Parser"] == parser and row[memoryColumn]))
        ax.errorbar(x, y, y_err, fmt=COLORS[parser], label=parser, ecolor="k", elinewidth=1, capsize=2, barsabove=True, clip_on=False)

    ax.set_xlim(0)
    ax.set_ylim(0)

    ax.legend(*ax.get_legend_handles_labels(), loc="lower center", bbox_to_anchor=(0.5, 1.0), ncol=4)

    fig.tight_layout()
    return fig


def plot_memory_incremental(rows):
    plot_size = (8, 6)
    fig, ax = base_plot(plot_size, "Memory usage in incremental mode", "Change size (bytes)", "Memory (MiB)")

    parsers = list(set(row["Parser"] for row in rows if "incremental" in row["Parser"]))
    for parser in parsers:
        for garbage in ["incl", "excl"]:
            color = "bv" if garbage == "incl" else "b^"
            label = "During parsing" if garbage == "incl" else "Cache increase"
            memoryColumn = f"Memory ({garbage}. garbage)"
            memoryErrorColumn = f"Error {garbage}."

            x, y, y_err = zip(*((int(row["Added"]) + int(row["Removed"]), float(row[memoryColumn]) / MiB, float(row[memoryErrorColumn] or 0) / MiB)
                                for row in rows if row["Parser"] == parser and row[memoryColumn]))
            ax.errorbar(x, y, y_err, fmt=color, label=label, ecolor="k", elinewidth=1, capsize=2, barsabove=True, clip_on=False)

    ax.set_xlim(0)
    ax.set_ylim(0)

    ax.legend(*ax.get_legend_handles_labels(), loc="lower center", bbox_to_anchor=(0.5, 1.0), ncol=4)

    fig.tight_layout()
    return fig


def plot_times(rows, parser_types):
    n = len(rows)
    plot_size = (8 if n < 50 else 12 if n < 100 else 18 if n < 200 else 24, 6)
    fig, ax1 = base_plot(plot_size, "Parse time and change size per version", "Version number", "Parse time (ms)")

    # Plot lines on ax2 below those on ax1 (https://stackoverflow.com/a/57307539)
    ax1.set_zorder(1)  # default z order is 0 for ax1 and ax2
    ax1.patch.set_visible(False)  # prevents ax1 from hiding ax2

    ax2 = ax1.twinx()  # instantiate a second axis that shares the same x-axis
    ax2.margins(*(0.1 / x for x in plot_size))
    ax2.set_ylabel("Change size (bytes)", color="y")
    ax2.tick_params(labelcolor="y")

    x, y = zip(*((int(row["i"]), int(row["Removed"]) + int(row["Added"])) for row in rows if row["Added"]))
    ax2.plot(x, y, "yo", label="Change size", markersize=3)  # clip_on=False won't work here due to custom z-order

    for column in parser_types:
        x, y, y_err = zip(*((int(row["i"]), float(row[column]), float(row[column + " Error (99.9%)"] or 0))
                            for row in rows if row[column]))
        ax1.errorbar(x, y, y_err, fmt=COLORS[column], label=column, ecolor="k", elinewidth=1, capsize=2, barsabove=True, clip_on=False)

    # Combine legends for both axes (https://stackoverflow.com/a/10129461)
    lines1, labels1 = ax1.get_legend_handles_labels()
    lines2, labels2 = ax2.get_legend_handles_labels()
    ax2.legend(lines1 + lines2, labels1 + labels2, loc="lower center", bbox_to_anchor=(0.5, 1.0), ncol=4)

    max_i = max(float(row["i"]) for row in rows)
    ax1.set_xlim(-max_i / 50, max_i + max_i / 50)
    ax1.set_ylim(0)
    ax2.set_ylim(0)

    fig.tight_layout()
    return fig


def plot_times_vs_changes(rows, unit, *changes):
    fig, ax = base_plot((8, 6), "Parse time vs. change size", f"Change size ({unit})", "Parse time (ms)")

    x, y = zip(*((sum(int(row[change]) for change in changes), float(row["Incremental"]))
                 for row in rows if row["Incremental"]))
    ax.plot(x, y, COLORS["Incremental"], label="Incremental", markersize=3, clip_on=False)

    ax.set_xlim(0)
    ax.set_ylim(0)

    fig.tight_layout()
    return fig


def plot_times_vs_changes_3d(rows):
    fig, ax = base_plot((6, 6, 6), "Parse time vs. change size", "Change size (bytes)", "Change size (chunks)", "Parse time (ms)",
                        {"projection": "3d"})

    x, y, z = zip(*((int(row["Added"]) + int(row["Removed"]), int(row["Changes"]), float(row["Incremental"]))
                    for row in rows if row["Incremental"]))
    # ax.plot(x, y, z, COLORS["Incremental"], label="Incremental", markersize=3)
    for xi, yi, zi in zip(x, y, z):
        ax.add_line(art3d.Line3D(*zip((xi, yi, 0), (xi, yi, zi)), color='g', marker='o', markersize=3, markevery=(1, 1)))

    # We need to manually set the upper limit here, because we don't call a proper plot function but manually draw lines
    ax.set_xlim3d(0, max(1, *x))
    ax.set_ylim3d(0, max(1, *y))
    ax.set_zlim3d(0, max(1, *z))

    ax.view_init(elev=30, azim=-45)
    return fig


def main():
    incremental_results_dir = path.join(DATA_DIR, "results", "incremental")

    if path.isdir(incremental_results_dir):
        print("Creating plots for incremental...")
        for language in scandir(incremental_results_dir):
            print(f" {language.name}")
            for result_file in scandir(language.path):
                csv_basename = ".".join(result_file.name.split(".")[:-1])
                print(f"  {csv_basename}")

                result_data = read_csv(result_file.path)
                result_data[0]["Added"] = None
                result_data_except_first = result_data[1:]

                figure_path = path.join(FIGURES_DIR, "incremental", language.name, csv_basename)
                makedirs(figure_path, exist_ok=True)

                figures = [
                    (plot_times(result_data, ["Batch", "Incremental", "IncrementalNoCache"]), "report"),
                    (plot_times(result_data_except_first, ["Incremental"]), "report-except-first"),
                    (plot_times_vs_changes(result_data_except_first, "bytes", "Added", "Removed"),
                     "report-time-vs-bytes"),
                    (plot_times_vs_changes(result_data_except_first, "chunks", "Changes"), "report-time-vs-changes"),
                    (plot_times_vs_changes_3d(result_data_except_first), "report-time-vs-changes-3D"),
                ]

                for fig, name in figures:
                    fig.savefig(path.join(figure_path, name + ".pdf"))
                    fig.savefig(path.join(figure_path, name + ".svg"))

                plt.close("all")

                merged_path = path.join(figure_path, "merged.pdf")
                if path.exists(merged_path):
                    remove(merged_path)
                pdftools.pdf_merge([path.join(figure_path, name + ".pdf") for _, name in figures], merged_path)

    memory_benchmarks_dir = path.join(DATA_DIR, "memoryBenchmarks")
    
    if path.isdir(memory_benchmarks_dir):
        print("Creating plots for memory benchmarks...")
        for language in scandir(memory_benchmarks_dir):
            print(f"  {language.name}")
            result_data_batch = read_csv(path.join(language.path, "batch.csv"))
            result_data_incremental = read_csv(path.join(language.path, "incremental.csv"))

            figure_path = path.join(FIGURES_DIR, "memoryBenchmarks", language.name)
            makedirs(figure_path, exist_ok=True)

            figures = [
                (plot_memory_batch(result_data_batch, "incl"), "report-full-garbage"),
                (plot_memory_batch(result_data_batch, "excl"), "report-cache-size"),
                (plot_memory_incremental(result_data_incremental), "report-incremental"),
            ]

            for fig, name in figures:
                fig.savefig(path.join(figure_path, name + ".pdf"))
                fig.savefig(path.join(figure_path, name + ".svg"))

            plt.close("all")

            merged_path = path.join(figure_path, "merged.pdf")
            if path.exists(merged_path):
                remove(merged_path)
            pdftools.pdf_merge([path.join(figure_path, name + ".pdf") for _, name in figures], merged_path)


if __name__ == '__main__':
    main()
