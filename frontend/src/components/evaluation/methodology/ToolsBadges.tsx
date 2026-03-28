import { TOOLS_USED } from '@/lib/data/methodology';

const CATEGORY_COLORS: Record<string, string> = {
  Evaluation: 'bg-blue-100 text-blue-800',
  NLP: 'bg-purple-100 text-purple-800',
  AI: 'bg-green-100 text-green-800',
  Language: 'bg-amber-100 text-amber-800',
  Framework: 'bg-teal-100 text-teal-800',
  Validation: 'bg-pink-100 text-pink-800',
  Visualization: 'bg-orange-100 text-orange-800',
  'CI/CD': 'bg-gray-100 text-gray-800',
};

/**
 * ToolsBadges — Technology skill tags grouped by category.
 */
export function ToolsBadges() {
  // Group tools by category
  const grouped = TOOLS_USED.reduce<Record<string, Array<typeof TOOLS_USED[number]>>>((acc, tool) => {
    const key = tool.category;
    if (!acc[key]) acc[key] = [];
    acc[key].push(tool);
    return acc;
  }, {});

  return (
    <section>
      <h2 className="text-2xl font-bold mb-2">Tools &amp; Technologies</h2>
      <p className="text-muted-foreground mb-6 max-w-3xl">
        Technologies used across the evaluation pipeline — from dataset construction to scoring to visualization.
      </p>

      <div className="space-y-4">
        {Object.entries(grouped).map(([category, tools]) => (
          <div key={category}>
            <p className="text-xs font-medium text-muted-foreground mb-2 uppercase tracking-wider">{category}</p>
            <div className="flex flex-wrap gap-2">
              {tools.map((tool) => (
                <span
                  key={tool.name}
                  className={`inline-flex items-center px-3 py-1.5 rounded-full text-sm font-medium ${CATEGORY_COLORS[category] || 'bg-muted'}`}
                  title={tool.description}
                >
                  {tool.name}
                </span>
              ))}
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
