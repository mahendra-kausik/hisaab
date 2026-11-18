"use client";

import { useState } from "react";
import { Check, ChevronsUpDown, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useWorkspace } from "@/lib/workspace";
import { NewWorkspaceDialog } from "./new-workspace-dialog";

export function WorkspaceSwitcher() {
  const { active, workspaces, setActive } = useWorkspace();
  const [dialogOpen, setDialogOpen] = useState(false);

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger
          render={
            <Button variant="outline" className="h-11 w-full justify-between gap-2 px-3" />
          }
        >
          <span className="truncate font-medium">{active.name}</span>
          <ChevronsUpDown className="h-4 w-4 text-muted-foreground" />
        </DropdownMenuTrigger>
        <DropdownMenuContent align="start" className="min-w-56">
          <DropdownMenuGroup>
            <DropdownMenuLabel>Workspaces</DropdownMenuLabel>
            {workspaces.map((workspace) => (
              <DropdownMenuItem
                key={workspace.id}
                className="min-h-11 justify-between"
                onClick={() => setActive(workspace.id)}
              >
                <span className="min-w-0">
                  <span className="block truncate">{workspace.name}</span>
                  <span className="block text-xs capitalize text-muted-foreground">
                    {workspace.kind}
                  </span>
                </span>
                {workspace.id === active.id && <Check className="h-4 w-4" aria-label="Active" />}
              </DropdownMenuItem>
            ))}
          </DropdownMenuGroup>
          <DropdownMenuSeparator />
          <DropdownMenuItem className="min-h-11" onClick={() => setDialogOpen(true)}>
            <Plus className="h-4 w-4" />
            New workspace
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>

      <NewWorkspaceDialog open={dialogOpen} onOpenChange={setDialogOpen} />
    </>
  );
}
