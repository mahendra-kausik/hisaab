"use client";

import { useState } from "react";
import Link from "next/link";
import { Archive, ArchiveRestore, Building2, MoreVertical, Pencil, Plus } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Skeleton } from "@/components/ui/skeleton";
import { ApiError } from "@/lib/api/client";
import { useProjects, useUpdateProject, type Project } from "@/lib/queries/projects";
import { cn } from "@/lib/utils";
import { useWorkspace } from "@/lib/workspace";
import { ProjectDialog } from "./project-dialog";

function ProjectRow({ project, onEdit }: { project: Project; onEdit: () => void }) {
  const { active } = useWorkspace();
  const update = useUpdateProject(active.id);
  const archived = !!project.archived_at;

  function setArchived(value: boolean) {
    update.mutate(
      { id: project.id, body: { archived: value } },
      {
        onSuccess: () => toast(value ? `${project.name} archived` : `${project.name} restored`),
        onError: (e) =>
          toast.error(e instanceof ApiError ? e.message : "Could not update the project"),
      },
    );
  }

  return (
    <li className="flex items-center">
      {/* Opens the Transactions page filtered to this project. */}
      <Link
        href={`/transactions?project_id=${project.id}`}
        className="flex min-h-14 min-w-0 flex-1 items-center gap-3 rounded-l-xl px-3 py-2 outline-none hover:bg-subtle focus-visible:ring-3 focus-visible:ring-ring/50"
      >
        <Building2 className="h-5 w-5 shrink-0 text-muted-foreground" aria-hidden="true" />
        <span className="min-w-0 flex-1">
          <span
            className={cn("block truncate text-sm font-medium", archived && "text-muted-foreground")}
          >
            {project.name}
          </span>
          {project.notes && (
            <span className="block truncate text-xs text-muted-foreground">{project.notes}</span>
          )}
        </span>
      </Link>

      <DropdownMenu>
        <DropdownMenuTrigger
          render={<Button variant="ghost" size="icon" className="size-11 shrink-0" />}
        >
          <MoreVertical className="h-4 w-4" />
          <span className="sr-only">Actions for {project.name}</span>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem className="min-h-11" onClick={onEdit}>
            <Pencil className="h-4 w-4" />
            Edit
          </DropdownMenuItem>
          <DropdownMenuItem className="min-h-11" onClick={() => setArchived(!archived)}>
            {archived ? <ArchiveRestore className="h-4 w-4" /> : <Archive className="h-4 w-4" />}
            {archived ? "Unarchive" : "Archive"}
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </li>
  );
}

export function ProjectsView() {
  const { active } = useWorkspace();
  const query = useProjects(active.id);
  // null = closed; { project: null } = adding; { project } = editing that one.
  const [dialog, setDialog] = useState<{ project: Project | null } | null>(null);

  const projects = query.data ?? [];
  const open = projects.filter((p) => !p.archived_at);
  const archived = projects.filter((p) => p.archived_at);

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold">Projects</h1>
        <Button className="h-11 gap-2" onClick={() => setDialog({ project: null })}>
          <Plus className="h-4 w-4" />
          Add project
        </Button>
      </div>

      {query.isError ? (
        <div className="flex flex-col items-start gap-3 py-6">
          <p>We could not load your projects.</p>
          <Button className="h-11" onClick={() => query.refetch()}>
            Try again
          </Button>
        </div>
      ) : query.isPending ? (
        <div className="flex flex-col gap-2" aria-busy="true">
          {Array.from({ length: 3 }, (_, i) => (
            <Skeleton key={i} className="h-14 w-full" />
          ))}
        </div>
      ) : (
        <>
          {open.length === 0 ? (
            <div className="flex flex-col items-start gap-3 rounded-xl border bg-surface p-6">
              <p>No projects yet — Add the properties or sites you track money against.</p>
              <Button className="h-11" onClick={() => setDialog({ project: null })}>
                Add project
              </Button>
            </div>
          ) : (
            <ul className="divide-y rounded-xl border bg-surface">
              {open.map((p) => (
                <ProjectRow key={p.id} project={p} onEdit={() => setDialog({ project: p })} />
              ))}
            </ul>
          )}

          {archived.length > 0 && (
            <section aria-labelledby="archived-heading" className="flex flex-col gap-2">
              <h2 id="archived-heading" className="px-1 text-sm font-medium text-muted-foreground">
                Archived
              </h2>
              <ul className="divide-y rounded-xl border bg-surface">
                {archived.map((p) => (
                  <ProjectRow key={p.id} project={p} onEdit={() => setDialog({ project: p })} />
                ))}
              </ul>
            </section>
          )}
        </>
      )}

      {dialog && <ProjectDialog editing={dialog.project} onClose={() => setDialog(null)} />}
    </div>
  );
}
