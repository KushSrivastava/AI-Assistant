import { useState, useCallback } from 'react';

// Adapter Pattern standard interface definition implicitly defined:
// FileNode {
//   name: string;
//   isDirectory: boolean;
//   children?: FileNode[]; // if isDirectory
// }

export function useFileTree() {
  const [nodes, setNodes] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const fetchTree = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      // Future: fetch('http://localhost:8080/api/v1/workspace/list')
      // For now, simulating an Agent tool / backend fallback return.
      const mockStructure = [
        {
          name: 'src',
          isDirectory: true,
          children: [
            {
              name: 'main',
              isDirectory: true,
              children: [
                { name: 'java', isDirectory: true, children: [{ name: 'App.java', isDirectory: false }] }
              ]
            }
          ]
        },
        { name: 'pom.xml', isDirectory: false },
        { name: 'README.md', isDirectory: false }
      ];

      await new Promise(resolve => setTimeout(resolve, 600)); // mock network delay
      setNodes(mockStructure);
    } catch (err) {
      setError(err.message || 'Failed to fetch tree');
    } finally {
      setLoading(false);
    }
  }, []);

  return { nodes, loading, error, refreshTree: fetchTree };
}
